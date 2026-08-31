# BudgetApp projectspecificatie

Deze repository is de source of truth voor BudgetApp.

## Architectuur

BudgetApp is een lokale Android-app. De interface en gedeelde kernlogica staan in `app/src/main/assets/` en draaien lokaal in de APK. GitHub Pages gebruikt diezelfde bestanden uitsluitend als tijdelijke mobiele preview tijdens ontwikkeling. GitHub, Pages en een webserver zijn geen runtime-afhankelijkheden van de uiteindelijke app.

Android-specifieke functies worden lokaal gekoppeld via een platformbridge:
- Room/SQLite voor financiële opslag;
- CameraX en gebundelde ML Kit OCR voor bonnen;
- lokale PDF-selectie, PdfRenderer en gebundelde ML Kit OCR voor bankafschriften;
- Android NotificationListenerService voor Rabobank- en Google Wallet-notificaties.

De Pages-preview mag browseropslag alleen als tijdelijke previewfallback gebruiken. Functionaliteit mag niet afhankelijk worden gemaakt van een webserver, GitHub API of cloudopslag.

## Privacy en opslag

- Alle financiële gegevens blijven lokaal op het toestel.
- Room/SQLite is de primaire Android-database.
- Bon- en PDF-OCR gebeurt lokaal met het gebundelde ML Kit-model.
- Geen externe analyse- of synchronisatieservice nodig.
- Rabobank- en Google Wallet-notificaties worden uitsluitend lokaal verwerkt.

## Interface

De visuele basis is het door de gebruiker aangeleverde `Budget — layout prototype v3`:
- donkere achtergrond `#0f1116`;
- maximale mobiele contentbreedte 480 px;
- afgeronde kaarten en bottom sheets;
- groene primaire actie, blauwe secundaire accenten;
- vaste onderste navigatie met Home, Budget, Transacties en Overzicht;
- Home bevat saldo, maandinkomsten/-uitgaven, spaardoel, uitgavecontrole, budgetpotjes, nog-indelen, laatste transacties en snel toevoegen.

Geen generieke vervangende layout gebruiken wanneer deze interface al is afgesproken.

## Potjes

Elk potje heeft zelfstandig een periode:
- week;
- salarisperiode;
- maand;
- jaar;
- eenmalig.

De salarisperiode loopt van de 23e tot en met de 22e.

Standaardpotjes:
- Vrije uitgaven: €80 per week;
- Boodschappen: €180 per salarisperiode;
- Brandstof: €60 per salarisperiode.

Potjes moeten lokaal toegevoegd, gewijzigd en verwijderd kunnen worden. Verwijdering mag transacties niet verwijderen; koppelingen naar het verwijderde potje worden losgemaakt.

Jaarlijkse heffingen worden apart herkend en niet als zichtbaar potje getoond. Waterheffing is een voorbeeld.

## Onderwerpen en categorieën

Onderwerpen/categorieën zijn niet beperkt tot de drie standaardpotjes. Minimaal beschikbaar in uitgavecontrole, handmatige transacties, onbekende transacties en lokaal leergeheugen:
- Boodschappen;
- Vrije uitgaven;
- Brandstof;
- Elektronica;
- Games / hobby;
- Auto / motor;
- Kleding;
- Huishouden;
- Vaste lasten;
- Inkomen;
- Anders.

Een onderwerp hoeft geen eigen budgetbedrag te hebben. Budgetcontrole rekent alleen met een werkelijk gekozen bestaand potje en mag geen verzonnen budgetbedragen gebruiken.

## Bekende correcties

- Geen maandelijkse stroomkosten als vaste aanname.
- Pararius is afgezegd en mag niet als actieve vaste last worden vooringevuld.
- VriendenLoterij is afgezegd en mag niet als actieve vaste last worden vooringevuld.
- Brandstof is niet €230 per maand; het budget is €60 per salarisperiode.
- Geen andere vaste lasten of bedragen voorinvullen die niet expliciet zijn afgesproken.

## Spaardoel

- Standaard spaardoel: €30.000.
- Toon huidig bedrag, doelbedrag en resterend bedrag.
- Schat maanden tot doel alleen bij een positieve recente netto spaartrend.
- Doel, naam en huidige stand zijn lokaal bewerkbaar.

## Transacties

Bronnen:
- handmatig;
- Rabobank-notificatie;
- Google Wallet-notificatie;
- PDF-bankafschrift;
- bon.

Eisen:
- compacte transactielijst;
- details bij aantikken;
- lege detailvelden niet tonen;
- transacties lokaal bewerkbaar en verwijderbaar;
- handmatige invoer blijft mogelijk;
- categorie en potje zijn afzonderlijk instelbaar;
- één daadwerkelijke banktransactie mag het saldo maar één keer beïnvloeden;
- filters werken op echte opgeslagen transacties, niet op demo-inhoud.

## Uitgavecontrole

`Wat wil je uitgeven?` gebruikt uitsluitend actuele lokale gegevens. De gebruiker kiest een onderwerp, bestaand potje en bedrag. De app vergelijkt het bedrag met het werkelijke resterende budget in de huidige periode. Geen hardcoded fictieve bedragen of fictieve spaardoelvertragingen gebruiken.

## Notificaties

- Gebruik Rabobank- en Google Wallet-notificaties voor live registratie/verificatie van betalingen.
- Een onbekende merchant/item komt in de leerwachtrij.
- De gebruiker kiest onderwerp en eventueel potje.
- De keuze wordt lokaal opgeslagen als merchant- of itemregel.
- De app mag een lokale notificatie tonen om de gebruiker naar de leerwachtrij te brengen.
- PDF-data mag een notificatierecord alleen corrigeren bij een strikte match op bedrag/richting, datum, tijd, merchant, kaart en referentie.

## PDF-bankimport

- PDF wordt handmatig door de gebruiker geselecteerd.
- OCR gebeurt lokaal op Android.
- Inkomsten en uitgaven worden gededupliceerd.
- Overlappende of opnieuw geïmporteerde PDF's voegen uitsluitend ontbrekende transacties toe.
- Begin- en eindsaldo worden herkend wanneer aanwezig.
- `beginsaldo + unieke mutaties = eindsaldo` wordt gecontroleerd.
- Alleen een gevalideerd eindsaldo mag als bekend banksaldo worden opgeslagen.
- Jaarlijkse heffingen worden herkend en buiten zichtbare potjes gehouden.

## Bonnen

- CameraX voor cameracapture.
- Gebundelde ML Kit OCR.
- Een bon mag zelfstandig worden opgeslagen als er nog geen banktransactie beschikbaar is.
- Dubbelscans van dezelfde herkende bontekst mogen niet dubbel tellen.
- Als een passende banktransactie al bestaat, wordt de bon daaraan gekoppeld en telt het saldo niet dubbel.
- Bonregels worden apart opgeslagen.
- Elke bonregel kan een ander onderwerp en potje krijgen.
- Bekende items worden via lokaal leergeheugen opnieuw toegewezen.
- Onbekende items komen in de leerwachtrij.
- Wanneer een banktransactie later wordt geïmporteerd, verhuizen bonregels naar die banktransactie en telt de oorspronkelijke bontransactie niet meer mee voor saldo.

## Vaste lasten

- Vaste lasten zijn lokaal bewerkbaar, toevoegbaar en verwijderbaar.
- Periode, vervaldag en actiefstatus zijn instelbaar.
- Jaarlijkse heffingen kunnen apart worden gemarkeerd.
- Geen voorbeeldabonnementen of voorbeeldbedragen opslaan als echte data.

## Definition of done

Een onderdeel is niet klaar wanneer alleen het scherm of een toast bestaat. Het is pas klaar wanneer de volledige lokale keten werkt: invoeren of importeren, valideren, opslaan, opnieuw laden, bewerken waar van toepassing en correct doorrekenen in saldo/potjes/overzichten.

Gebruik geen demo-data, nepacties of placeholderfunctionaliteit als vervanging voor afgesproken functionaliteit. Pages mag Android-only functies als niet beschikbaar aanduiden omdat Pages alleen preview is; de lokale Android-implementatie van die functies moet de werkelijke eindimplementatie zijn.

## Technische baseline

- Android Java 17;
- Android Gradle Plugin 9.3.0;
- Gradle 9.5.0;
- compileSdk/targetSdk 36, minSdk 23;
- Room 2.8.4;
- CameraX 1.6.1;
- ML Kit Text Recognition 16.0.1, gebundeld model;
- Android WebView laadt uitsluitend de lokale assets als hoofdinterface.

## Repositoryregels

- Geen README toevoegen tenzij de gebruiker daar expliciet om vraagt.
- Geen overbodige codecomments of toelichtingsbestanden toevoegen.
- Bestaande afgesproken functies, onderwerpen, categorieën of schermen niet verwijderen tijdens een andere wijziging.
- Voor structurele wijzigingen eerst controleren op regressies tegen deze specificatie en de actuele code.