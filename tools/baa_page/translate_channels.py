# Translates Russian posts in baa/api/data/<channel>.json into English (field "en"), so the
# main page can show the channels' quotes and questions in English. Posts that already have
# a translation of the same text are skipped. Uses OpenRouter; run from anywhere:
#     python3 tools/baa_page/translate_channels.py
import json, os, re, sys, time, urllib.request

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'baa', 'api', 'data')
MODEL = 'anthropic/claude-sonnet-5.5'
BATCH = 25
CYR = re.compile('[А-Яа-яЁё]')

PROMPT = {
  'baaqod': ('These are quotes from books, saved in a personal Telegram channel. Translate each into '
             'natural English. If the quote is a Russian translation of an English book, still just '
             'translate it; do not try to recall the original wording.'),
  'q_of_t_d': ('These are short questions from a personal Telegram channel, "Question of the Day": '
               'informal, often lowercase, sometimes playful. Translate each into natural English and '
               'keep the informal tone, the lowercase style and the punctuation habits.'),
}

def ask(channel, items):
    body = {
        'model': MODEL,
        'messages': [{'role': 'user', 'content':
            PROMPT[channel] + ' Keep line breaks. Return only a JSON object mapping each id to its '
            'translation, nothing else.\n\n' + json.dumps(items, ensure_ascii=False)}],
        'temperature': 0.2, 'max_tokens': 6000,
    }
    req = urllib.request.Request('https://openrouter.ai/api/v1/chat/completions',
        data=json.dumps(body).encode(), headers={'Content-Type': 'application/json', 'Authorization': 'Bearer x'})
    with urllib.request.urlopen(req, timeout=180) as r:
        text = json.load(r)['choices'][0]['message']['content']
    text = text[text.index('{'):text.rindex('}') + 1]
    return json.loads(text)

for ch in ('baaqod', 'q_of_t_d'):
    path = os.path.join(ROOT, f'{ch}.json')
    data = json.load(open(path, encoding='utf-8'))
    todo = {i: p['t'] for i, p in data['posts'].items()
            if CYR.search(p['t']) and len(p['t']) >= 15 and p.get('en_of') != p['t']}
    ids = sorted(todo, key=int)
    print(ch, 'to translate:', len(ids), flush=True)
    for k in range(0, len(ids), BATCH):
        part = {i: todo[i] for i in ids[k:k + BATCH]}
        for attempt in range(3):
            try:
                out = ask(ch, part); break
            except Exception as e:
                print('  retry', k, e, flush=True); time.sleep(5)
        else:
            continue
        for i, en in out.items():
            if i in part and isinstance(en, str) and en.strip():
                data['posts'][i]['en'] = en.strip(); data['posts'][i]['en_of'] = part[i]
        json.dump(data, open(path, 'w', encoding='utf-8'), ensure_ascii=False)
        print(f'  {min(k + BATCH, len(ids))}/{len(ids)}', flush=True)
