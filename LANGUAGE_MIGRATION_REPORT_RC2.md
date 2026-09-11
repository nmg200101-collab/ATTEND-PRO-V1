# ATTEND PRO — Arabic / English Migration Report (RC2)

## Implemented
- Persisted app-language manager shared by Store and Employee.
- Android 13+ per-app locale integration.
- Arabic and English declared in locales_config.xml.
- RTL Arabic / LTR English in updated primary screens.
- Localized app names and primary dashboard resource strings.
- Practical searchable Store and Employee guides in Arabic and English.
- Store shift editor localized with locale-aware AM/PM formatting.
- Language selector in Store and Employee menus.
- Privacy and Data center in Arabic and English.
- Receiver/management-phone screens implemented bilingually.
- Shared UiKit now translates legacy common labels when English is selected and uses LTR direction for English tiles/dialogs.
- Store Management and System Administration shared dialog surfaces route legacy labels through the English migration bridge.

## Current audit status
The source audit intentionally reports raw hard-coded Arabic literals even when a literal is passed through a runtime bilingual/shared translation surface. It therefore remains a migration inventory rather than proof that every reported occurrence is visible in Arabic at runtime.

Primary operational paths now have English coverage, but legacy secondary diagnostic/admin text is still being audited. The release remains RC2 and the Play listing must not claim 100% English coverage until a real-device English walkthrough confirms no user-facing Arabic remains.

## Required field verification
Run both applications in English and exercise:
- Main / Classic / Sections
- Store Management
- employee management and pairing
- attendance / checkout
- messages and offline reply
- receiver phone permissions
- remote Store settings
- backups / lock
- System Administration
- troubleshooting dialogs

Any visible Arabic system text (excluding user-entered Arabic names/content) is a release blocker for declaring English migration complete.
