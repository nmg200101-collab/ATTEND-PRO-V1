# ATTEND PRO — Arabic / English Migration Report (RC2)

## Implemented
- Persisted app-language manager shared by Store and Employee.
- Android 13+ per-app locale integration.
- Arabic and English declared in `locales_config.xml`.
- RTL Arabic / LTR English in updated primary screens.
- Localized app names.
- Searchable offline Store and Employee guides in Arabic and English.
- Store primary dashboard core labels localized.
- Employee primary dashboard core labels bilingual.
- Store shift editor localized with locale-aware AM/PM formatting.
- Language selector in Store and Employee menus.
- Shared Privacy and Data center in Arabic and English.

## Remaining audit
Legacy secondary/admin screens still contain historical hard-coded Arabic strings. They must be migrated before English coverage is declared 100%.

## Release rule
Do not claim complete English support in the Play listing until the hard-coded user-facing Arabic audit is cleared or intentionally exempted for protocol/internal audit text.
