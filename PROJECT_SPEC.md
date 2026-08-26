# BudgetApp projectspecificatie

Deze repository is vanaf 26-08-2026 de source of truth voor BudgetApp.

## Productdoel

Een native Android-budgetapp die inkomsten, uitgaven, potjes, vaste lasten en sparen lokaal bijhoudt. De app moet zonder cloudaccount bruikbaar zijn en uiteindelijk als APK gebouwd kunnen worden.

## Privacy en opslag

- Alle financiële gegevens blijven lokaal op het toestel.
- Room/SQLite is de primaire database.
- Bon- en PDF-OCR gebeurt lokaal met het gebundelde ML Kit-model.
- Geen externe analyse- of synchronisatieservice nodig.
- Rabobank- en Google Wallet-notificaties worden uitsluitend lokaal verwerkt.

## Potjes

Elk potje heeft zelfstandig een periode:
- week
- salarisperiode
- maand
- jaar
- eenmalig

De salarisperiode loopt van de 23e tot en met de 22e.

Standaardpotjes:
- Vrije uitgaven: €80 per week
- Boodschappen: €180 per salarisperiode
- Brandstof: €60 per salarisperiode

Jaarlijkse heffingen worden apart herkend en niet als zichtbaar potje getoond. Waterheffing is een voorbeeld.

## Bekende correcties uit de projectspecificatie

- Geen maandelijkse stroomkosten als vaste aanname.
- Pararius is afgezegd en mag niet als actieve vaste last worden vooringevuld.
- VriendenLoterij is afgezegd en mag niet als actieve vaste last worden vooringevuld.
- Brandstof is niet €230 per maand; het afgesproken budget is €60 per salarisperiode.

## Spaardoel

- Standaard spaardoel: €30.000.
- Toon het huidige bedrag, doelbedrag en resterend bedrag.
- Toon een geschat aantal maanden tot het doel wanneer er een positieve recente netto spaartrend beschikbaar is.
- Doel en huidig bedrag moeten bewerkbaar zijn.

## Transacties

Bronnen:
- handmatig
- Rabobank-notificatie
- Google Wallet-notificatie
- PDF-bankafschrift
- bon/receipt

Eisen:
- compacte transactielijst;
- details bij aantikken;
- lege detailvelden niet tonen;
- transacties moeten bewerkbaar en verwijderbaar zijn;
- handmatige invoer blijft mogelijk;
- één daadwerkelijke banktransactie mag het saldo maar één keer beïnvloeden.

## Notificaties

- Gebruik Rabobank- en Google Wallet-notificaties voor live registratie/verificatie van betalingen.
- Een onbekende merchant/item komt in de leerwachtrij.
- De gebruiker kan in de app een potje en categorie kiezen.
- De app mag een lokale notificatie tonen om de gebruiker naar die leerwachtrij te brengen.
- Een naam/omschrijving uit een PDF mag een notificatierecord alleen corrigeren bij een strikte match op bedrag/richting, datum, tijd, merchant, kaart en referentie.

## PDF-bankimport

- PDF wordt handmatig door de gebruiker geselecteerd.
- OCR gebeurt lokaal.
- Alle inkomsten en uitgaven worden gededupliceerd.
- Overlappende of opnieuw geïmporteerde PDF's voegen uitsluitend ontbrekende transacties toe.
- Begin- en eindsaldo worden herkend wanneer aanwezig.
- `beginsaldo + unieke mutaties = eindsaldo` wordt gecontroleerd.
- Alleen een gevalideerd eindsaldo mag als bekend banksaldo worden opgeslagen.
- Jaarlijkse heffingen worden herkend en buiten zichtbare potjes gehouden.

## Bonnen

- CameraX voor cameracapture.
- Gebundelde ML Kit OCR.
- Een bon mag zelfstandig als transactie worden opgeslagen, ook als er nog geen banktransactie beschikbaar is.
- Bonregels worden apart opgeslagen.
- Elke bonregel kan een ander potje/categorie krijgen.
- Bekende items worden via lokaal leergeheugen automatisch opnieuw toegewezen.
- Onbekende items komen in een wachtrij voor gebruikerskeuze.
- Wanneer een bon later aan een banktransactie wordt gekoppeld, verhuist de budgetverdeling naar de banktransactie en telt het saldo niet dubbel.

## Vaste lasten

- Vaste lasten zijn lokaal bewerkbaar/toevoegbaar/verwijderbaar.
- Periode en vervaldag zijn instelbaar.
- Jaarlijkse heffingen kunnen apart worden gemarkeerd.

## Interface

- Donkere interface.
- Compacte lijsten.
- Details pas na aantikken.
- Lege detailvelden verbergen.
- Hoofdnavigatie: Overzicht, Potjes, Transacties, Import, Instellingen.
- Geen niet-afgesproken vaste kosten of bedragen voorinvullen.

## Technische baseline

- Native Android.
- Java 17.
- Android Gradle Plugin 9.3.0.
- Gradle 9.5.0.
- compileSdk/targetSdk 36, minSdk 23.
- Room 2.8.4.
- CameraX 1.6.1.
- ML Kit Text Recognition 16.0.1, gebundeld model.
