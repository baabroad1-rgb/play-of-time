# Индекс документов Google Drive (папка «play of time»)

Папка: https://drive.google.com/drive/folders/1aC9cv460P-xKaI97zOKANlyBLrJUmk8C
Владелец: alexandr.belyshkin@gmail.com

Сверено с настоящим содержимым папки **26 сентября 2026**: в папке 37 файлов.
Предыдущая версия индекса (16 июля) знала 26 из них — **11 документов появились
позже и в индекс не попали**, включая все юридические документы и тексты для
Google Play. Индекс лежал в `handover/docs/` внутри июльского пакета передачи,
то есть в архиве — поэтому и не обновлялся. Теперь он в `docs/`, рядом с остальной
живой документацией.

Статус: 🟢 актуален · 🟡 частично устарел · 🔴 исторический
(🟡/🔴 — расшифровка расхождений в `../handover/OUTDATED_DOCS_AND_CURRENT_DECISIONS.md`)

**При противоречии документа и кода — прав код.** Документы в Drive никем
автоматически не обновляются.

---

## ⚠️ Требует внимания

| Что | Почему важно |
|---|---|
| **Два файла `PlayOfTime_PrivacyPolicy_20260825`** с одинаковым названием и размером (9157 б), но разными ID: [1cMDsp…](https://docs.google.com/document/d/1cMDsp1uM82zEmx_w2OEojIhO1FHT73wff4DoJrE9Vqs/edit) (изменён 02.09) и [1A76QV…](https://docs.google.com/document/d/1A76QV2GiOlQb7iXTPaY1uH0_ZlrN_HbeorbRqnRr2QI/edit) (25.08) | В Google Play Console указывается ссылка на политику конфиденциальности. Если сослаться на старую копию, опубликована будет не та версия. Лишнюю надо удалить, пока никто не перепутал |
| **Юридические документы оформлены под российское ИП**, а публикация планируется от лица чешского девелопера | Прямо сказано в `playoftime_marketing_plan` (08.09.2026): «заменить юридические черновики (сейчас оформлены под российское ИП) на версию под чешского издателя». Касается Privacy Policy и Terms of Use |
| **`developments_note_15.07.2026.docx`** — действующий список пожеланий Алекса по UI/UX, изменён 10.09.2026, 188 КБ | Это фактически бэклог, который живёт только в Drive и не пересекается с `TASKS.md`. Часть пунктов («поправить блок квеста», размеры шрифтов, прокрутка в режиме справки) могла быть уже сделана, часть — нет. Стоит сверить с `TASKS.md` и `CHANGELOG.md` и перенести незакрытое в репозиторий |

---

## Спецификации и метрики

| Документ | Статус | Что это | Изменён |
|---|---|---|---|
| [measures.docx](https://drive.google.com/file/d/1zpNBmk-mcWI2rSfbJHSY88ktuyJCQm-N/view) | 🟡 | Формулы и определения всех метрик | 02.12.2025 |
| [parametr_table.docx](https://drive.google.com/file/d/18qtsWESHN-jbp2B1eteDAzdAvJ7Rxx6f/view) | 🟡 | 38 параметров с точными расчётами | 04.12.2025 |
| [Play_of_Time_Spec_v2_CODE.docx](https://drive.google.com/file/d/17LHlVeseHh5uta8fEgIf29up9nY81E1c/view) | 🟡 | Спецификация v2 (код) | 11.06.2026 |
| [Play_of_Time_Spec_and_Backlog.docx](https://drive.google.com/file/d/1v98z0iUD7hIMGSXOSf6U8p7kKL47lX7o/view) | 🟡 | Спека + бэклог | 10.06.2026 |
| [сверху вниз.docx](https://drive.google.com/file/d/1Qz1YXnQwqxFtRHwPZgWc4hCkUpJO0Smx/view) | 🟡 | Концепция «сверху вниз» | 05.06.2026 |
| [list_of_quests.docx](https://drive.google.com/file/d/1UgAd9DHY_UdoBWxAQjJ6zQbb-434qbde/view) | 🔴 | Старая система из 10 квестов (сейчас 15) | 22.10.2025 |
| [quests_oder.pdf](https://drive.google.com/file/d/1TNJDbgs-GPTdXKfbQs5KbFWxqnIiK35p/view) | 🔴 | Блок-схема квестов с адаптацией (старая) | 25.06.2025 |
| [cheer_ups.docx](https://drive.google.com/file/d/10YW40ia0Zir_8AJul62F6l_YYVlEQmuP/view) | 🔴 | Старые триггеры и тексты подбадриваний | 02.12.2025 |

Действующие истины по этим темам — в коде: квесты `android/app/src/main/java/ru/iflex/timemvp/core/utils/Quest.kt`,
SCM `use_case/CalculateScmUseCase.kt`, подбадривания `core/utils/CheerUpMessages.kt`.

## Дизайн

| Документ | Статус | Что это | Изменён |
|---|---|---|---|
| [основной экран.pdf](https://drive.google.com/file/d/1V1xaC_mUyWLj43l0WEQGfiy-zIWiPS1y/view) | 🟡 | Дизайн-прототип главного экрана | 04.06.2026 |
| [Аналитический экран.pdf](https://drive.google.com/file/d/1WPN7z-fzlvekGOsGcUeaOZjwFKXgVYmd/view) | 🟡 | Дизайн-прототип аналитики | 04.06.2026 |
| [chernoff_face_reference_2026-09-15.md](https://drive.google.com/file/d/1rgyE3cdCQrW5rXxgx8nukcsAiOBnKtzy/view) | 🟢 | Референс лиц Чернова | 14.09.2026 |

Сверка с этими прототипами — Priority 1 и 3 в бэклоге `android/CLAUDE.md`.
Копия референса лиц Чернова есть и в репозитории: `webapp/handoff/chernoff_face_reference_2026-09-15.md`.

## Планы и аудиты

| Документ | Статус | Что это | Изменён |
|---|---|---|---|
| [developments_note_15.07.2026.docx](https://drive.google.com/file/d/1RFcWNRYRqGbkS2WybZL5NV_-XGpPRqDS/view) | 🟢 | **Действующий список пожеланий Алекса по UI/UX** (см. раздел «Требует внимания») | 10.09.2026 |
| [playoftime_marketing_plan](https://docs.google.com/document/d/1eMPUvckztFGez4n1NaIV_v7BOURLnpSU64pQjUKNDj8/edit) | 🟢 | План продвижения: чек-лист готовности к Google Play, каналы роста на 3–6 месяцев | 08.09.2026 |
| [plan_remaining_work.md](https://drive.google.com/file/d/1gLzjalI77VNvuTZX4gvOWLdYb1MapQ45/view) | 🟡 | Аудит 16.06 с пофайловыми фиксами (копия: `../handover/docs/plan_remaining_work.md`) | 16.06.2026 |
| [plan4revision_v10.docx](https://drive.google.com/file/d/1onua0w5GkYFONmQbiyU1A8hcGvJ86y5k/view) | 🔴 | План ревизии от продакт-оунера | 22.10.2025 |
| [plan_of_actions.docx](https://drive.google.com/file/d/1T8U1g4Giwrpcyo7wHFp1G3LoOI47t-3O/view) | 🔴 | Ранний план действий | 26.11.2025 |
| [prompt_4_AI_4_discuss.docx](https://drive.google.com/file/d/1ohhFdhNk_fZzxAoccFa9fszaU2y7yVdv/view) | 🔴 | Промпт для обсуждения с AI | 22.10.2025 |

## Google Play: тексты и юридические документы

Все семь появились после июльского индекса. Это то, без чего релиз не выйдет.

| Документ | Статус | Что это | Изменён |
|---|---|---|---|
| [full_description.txt](https://drive.google.com/file/d/1FUQaFZMvRxSHiqAnxgSOYatI20uJaoZS/view) | 🟢 | Полное описание для стора (EN) | 03.09.2026 |
| [short_description.txt](https://drive.google.com/file/d/1Wv020xUE5taRsYO1E6Xl_4wCaDa-DlLQ/view) | 🟢 | Короткое описание: «Time Perception, Self-Control & Inner Rhythm Training App.» | 03.09.2026 |
| [about4googleplay_rus_and_eng.docx](https://drive.google.com/file/d/1GwEgK96tXfKZ_MqwLNNl5PxTxPr5_TUJ/view) | 🟢 | Описания для Google Play, рус + англ | 25.08.2026 |
| [PlayOfTime_PrivacyPolicy_20260825](https://docs.google.com/document/d/1cMDsp1uM82zEmx_w2OEojIhO1FHT73wff4DoJrE9Vqs/edit) | 🟡 | Политика конфиденциальности, **более свежая из двух копий**. Оформлена под ИП — требует переделки под чешского издателя | 02.09.2026 |
| [PlayOfTime_PrivacyPolicy_20260825](https://docs.google.com/document/d/1A76QV2GiOlQb7iXTPaY1uH0_ZlrN_HbeorbRqnRr2QI/edit) | 🔴 | **Дубликат с тем же названием**, старее. Удалить, чтобы не сослаться на него по ошибке | 25.08.2026 |
| [PlayOfTime_TermsOfUse.2026-08-25.docx](https://drive.google.com/file/d/1RQfrZ4iUpWke9Sp0E7TqATTrJq4B9hXN/view) | 🟡 | Условия использования. Та же проблема с ИП | 27.08.2026 |
| [GooglePlay_DataSafety_Guide_20260825.md](https://docs.google.com/document/d/1qx_0EKclR-zDlOO6legxn1CIEyvoHIgGAVoi3Faf4q8/edit) | 🟢 | Как заполнять раздел Data Safety в Play Console | 25.08.2026 |

## Продукт, маркетинг, научная база

| Документ | Статус | Что это | Изменён |
|---|---|---|---|
| [site_content.docx](https://drive.google.com/file/d/1vPXN6DLaJmmx7DF5V8bMKiX17fhoAvIp/view) | 🟢 | Контент сайта playoftime.online | 07.07.2026 |
| [the myth.docx](https://drive.google.com/file/d/1yF-pwzke9wsGxaLt9d2HmOsOWHBNHrae/view) | 🟢 | Нарратив/миф игры | 07.07.2026 |
| [bait4investors.docx](https://drive.google.com/file/d/1gx25eUQCKcOixvVSx5gRF1Ke9t6s45D1/view) | 🟢 | Питч для инвесторов | 05.09.2025 |
| [Play_of_Time.pptx](https://drive.google.com/file/d/13D7aDNY6Ft2bhtwzEWkweSN-Jasj41gT/view) | 🟢 | Презентация | 15.03.2026 |
| [Play_of_Time__Mastery_of_Milliseconds.mp4](https://drive.google.com/file/d/1d356W65pemPuS91IK-6oMa8YQiAVpXzJ/view) | 🟢 | Промо-ролик (копия в `webapp/assets/play-of-time-video.mp4`) | 15.03.2026 |
| [about.docx](https://drive.google.com/file/d/1-cqDNHtaHKJnJeTYXVpADSQKSPRhmbt1/view) | 🔴 | Старый русский текст About | 23.10.2025 |
| [1.xlsx](https://drive.google.com/file/d/1kkxUilcxu6EXpes8CzhrcVHAbquEQp73/view) | 🟢 | Пример реального Excel-экспорта (20 тыс. попыток) | 21.06.2026 |
| [Методология_CUT_vol1.pdf](https://drive.google.com/file/d/1l1SqP7VZw7Bi6Zmxw4B11Oyy4G6C8kYP/view) | 🟢 | Научная база (Геллерштейн и др.), справочное чтение | 11.11.2024 |
| [Методология_психотехники_CUT_vol1.pdf](https://drive.google.com/file/d/1NcTOFBAtjtajAmTVl_K2m0XbGyV_yScD/view) | 🟢 | То же, другой том | 26.08.2024 |
| [Методология_психотехники_Предвосхищен_Z_Library.pdf](https://drive.google.com/file/d/1b7F_2zohOctqw5HbBhEX6yfqVGj24iGm/view) | 🟢 | Предвосхищение, часть 1 | 24.04.2024 |
| [Методология_психотехники_Предвосхищен_Z_Library_2.pdf](https://drive.google.com/file/d/1lsoa6Z3Lb45a0rAYlOhO4MsA9qaRbz-W/view) | 🟢 | Предвосхищение, часть 2 | 24.04.2024 |

## Прочее

| Документ | Что это |
|---|---|
| [universal-pm.skill](https://drive.google.com/file/d/1KhmFpT632iQB4LHfh79HkNn-gnFoKIv1/view) | Скилл Claude, выгрузка 16.06.2026 |
| [pm-play-of-time.skill](https://drive.google.com/file/d/15xRuUpBGqHaVN5mBL0BkT2UkHetIhm2i/view) | Скилл Claude, выгрузка 16.06.2026 |

---

## Почему документы остаются в Drive, а не переносятся в репозиторий

`.docx` и `.pdf` — бинарные форматы: git видит их как единый блоб и не показывает,
что именно изменилось. Их место — там, где их правят, то есть в Drive.
Переносить в репозиторий стоит только то, что становится рабочей спецификацией —
и в виде текста (`.md`), а не как есть.

Единственное, что действительно стоит перенести — **незакрытые пункты из
`developments_note_15.07.2026.docx`** в `docs/TASKS.md`, потому что сейчас это
второй бэклог, о котором репозиторий ничего не знает.
