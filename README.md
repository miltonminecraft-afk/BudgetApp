# BudgetApp

Native Android-budgetapp. **Deze GitHub-repository is de source of truth.**

De volledige functionele projectspecificatie staat in [`PROJECT_SPEC.md`](PROJECT_SPEC.md).

## Huidige baseline

De eerste Android Studio-baseline bevat:

- Room/SQLite lokale database;
- standaardpotjes volgens de afgesproken periodes en bedragen;
- salarisperiode 23e–22e;
- spaardoel van €30.000 met maandenschatting op basis van recente netto trend;
- handmatige transacties;
- compacte transactielijst en detailweergave;
- vaste lasten;
- Rabobank/Google Wallet `NotificationListenerService`;
- lokale leerwachtrij voor onbekende merchants/items;
- PDF-import via `PdfRenderer` + gebundelde ML Kit OCR;
- deduplicatie en begin/eindsaldo-controle;
- CameraX-bonscanner;
- bonregels die afzonderlijk aan potjes/categorieën kunnen worden gekoppeld;
- logica om bon en latere banktransactie zonder dubbele saldo-impact te koppelen.

## Openen in Android Studio

Open de repository-root als Gradle-project. De projectconfiguratie gebruikt:

- JDK 17
- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- Android SDK 36

Android Studio kan de Gradle-distributie uit `gradle/wrapper/gradle-wrapper.properties` gebruiken.

## Rechten op Android

Voor volledige werking moet de gebruiker zelf:

1. camera-toegang toestaan voor het scannen van bonnen;
2. notificatietoegang voor BudgetApp inschakelen via **Import > Notificatietoegang openen**;
3. op Android 13+ toestemming geven voor de lokale categorie-notificaties.

## Privacy

Financiële data, OCR-resultaten, regels en leerdata blijven lokaal op het toestel. Er is geen cloudaccount nodig.
