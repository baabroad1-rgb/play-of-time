# Merges translation files ({post id: English text}) into baa/api/data/<channel>.json as "en",
# remembering which original text was translated ("en_of"), so a later edit of the post is noticed.
#     python3 tools/baa_page/apply_translations.py <channel> <file.json> [<file.json> ...]
import json, os, sys
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'baa', 'api', 'data')
ch = sys.argv[1]; path = os.path.join(ROOT, f'{ch}.json')
data = json.load(open(path, encoding='utf-8')); n = 0
for f in sys.argv[2:]:
    for i, en in json.load(open(f, encoding='utf-8')).items():
        p = data['posts'].get(str(i))
        if p and en.strip():
            p['en'] = en.strip(); p['en_of'] = p['t']; n += 1
json.dump(data, open(path, 'w', encoding='utf-8'), ensure_ascii=False)
print(ch, 'translations applied:', n)
