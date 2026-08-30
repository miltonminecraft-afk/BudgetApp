# BudgetApp projectspecificatie

Deze repository is de source of truth voor BudgetApp.

## Productdoel

BudgetApp gebruikt één gedeelde HTML/CSS/JavaScript-interface die direct via GitHub Pages op mobiel en desktop getest kan worden en later als Android APK wordt verpakt. Android-specifieke functies worden via een adapter/native laag toegevoegd zonder de webinterface en budgetlogica te dupliceren.

## Privacy en opslag

- Financiële gegevens blijven lokaal op het apparaat.
- De webversie gebruikt lokale browseropslag.
- De Android-versie krijgt een lokale native opslagadapter met Room/SQLite.
- Geen cloudaccount nodig.
- OCR, categoriegeheugen en financiële verwerking mogen geen externe analyse- of synchronisatieservice vereisen.
- Rabobank- en Google Wallet-notificaties worden uitsluitend lokaal via de Android-adapter verwerkt.

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

## Bekende correcties

- Geen maandelijkse stroomkosten als vaste aanname.
- Pararius is afgezegd en mag niet als actieve vaste last worden vooringevuld.
- VriendenLoterij is afgezegd en mag niet als actieve vaste last worden vooringevuld.
- Brandstof is €60 per salarisperiode, niet €230 per maand.

## Spaardoel

- Standaard spaardoel: €30.000.
- Toon huidig bedrag, doelbedrag en resterend bedrag.
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
- transacties bewerkbaar en verwijderbaar;
- handmatige invoer blijft mogelijk;
- één daadwerkelijke banktransactie mag het saldo maar één keer beïnvloeden.

## Notificaties

- Gebruik Rabobank- en Google Wallet-notificaties voor live registratie/verificatie van betalingen op Android.
- Een onbekende merchant/item komt in de leerwachtrij.
- De gebruiker kan in de app een potje en categorie kiezen.
- De Android-app mag een lokale notificatie tonen om de gebruiker naar die leerwachtrij te brengen.
- Een naam/omschrijving uit een PDF mag een notificatierecord alleen corrigeren bij een strikte match op bedrag/richting, datum, tijd, merchant, kaart en referentie.

## PDF-bankimport

- PDF wordt handmatig door de gebruiker geselecteerd.
- OCR en parsing blijven lokaal.
- Alle inkomsten en uitgaven worden gededupliceerd.
- Overlappende of opnieuw geïmporteerde PDF's voegen uitsluitend ontbrekende transacties toe.
- Begin- en eindsaldo worden herkend wanneer aanwezig.
- `beginsaldo + unieke mutaties = eindsaldo` wordt gecontroleerd.
- Alleen een gevalideerd eindsaldo mag als bekend banksaldo worden opgeslagen.
- Jaarlijkse heffingen worden herkend en buiten zichtbare potjes gehouden.
- Web en Android gebruiken dezelfde import- en deduplicatielogica; alleen OCR/bestandsadapter mag per platform verschillen.

## Bonnen

- De webversie gebruikt browser-camera/bestandsmogelijkheden waar beschikbaar.
- Android gebruikt CameraX met lokaal gebundelde ML Kit OCR.
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
- Mobile-first.
- Compacte lijsten.
- Details pas na aantikken.
- Lege detailvelden verbergen.
- Hoofdnavigatie: Overzicht, Potjes, Transacties, Import, Instellingen.
- Geen niet-afgesproken vaste kosten of bedragen voorinvullen.
- De GitHub Pages-versie toont altijd de app via `index.html`, nooit README/documentatie als startpagina.

## Technische baseline

- GitHub-repository is de source of truth.
- Web-first HTML/CSS/JavaScript.
- `index.html` in de repository-root is de GitHub Pages entrypoint.
- Webopslag is lokaal en wordt via een opslagadapter afgeschermd zodat Android later Room/SQLite kan gebruiken.
- Android wordt later rond dezelfde webinterface gebouwd met een hybride wrapper/native adapterlaag.
- Android-specifieke camera, notificatie, OCR en opslagfuncties worden buiten de gedeelde UI/businesslogica gehouden.
- Geen onnodige README-bestanden of overbodige codecomments.
