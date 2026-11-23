# Poppy – guida di messa in opera

Poppy = app Android nativa (Kotlin + Jetpack Compose), login Google, database Firebase Firestore (gratuito), italiano/inglese.

## 1. Firebase (gratis, piano Spark)
1. Vai su https://console.firebase.google.com → **Aggiungi progetto** "poppy" (disattiva Google Analytics).
2. **Build → Authentication → Inizia → Accesso → Google → Abilita** (scegli email di supporto).
3. **Build → Firestore Database → Crea database**: posizione **eur3 (Europa)** o `europe-west`, modalità produzione.
4. Scheda **Regole**: incolla il contenuto di `firestore.rules` → Pubblica.
5. **Impostazioni progetto → Le tue app → Aggiungi app Android**: nome pacchetto `it.venturini.poppy`, e come **SHA-1** inserisci quello del tuo keystore (punto 2). Scarica **google-services.json** e mettilo in `app/google-services.json`.
   - Se aggiungi l'SHA-1 dopo, riscarica il file.

## 2. Keystore di firma (una volta sola, da conservare!)
Da PC con Java (o dentro Android Studio → Terminale):
```
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
keytool -list -v -keystore upload.jks -alias upload      # copia lo SHA-1 per Firebase
base64 -w0 upload.jks > upload.jks.b64                   # su Windows: certutil -encode oppure PowerShell [Convert]::ToBase64String
```
**Conserva `upload.jks` e le password in un posto sicuro (backup!).** Senza non puoi aggiornare l'app.

## 3. GitHub
1. Crea un repository **privato** `poppy` e carica tutto il contenuto di questa cartella (incluso `app/google-services.json`).
2. **Settings → Secrets and variables → Actions** → aggiungi: `KEYSTORE_BASE64` (contenuto di upload.jks.b64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (=upload), `KEY_PASSWORD`.
   (Se preferisci non committare google-services.json, mettilo nel secret `GOOGLE_SERVICES_JSON`.)
3. **Actions → Build Poppy → Run workflow**. A fine build scarichi `poppy-apk` (da installare sul telefono per provare) e `poppy-aab` (per il Play Store).
4. Se il build fallisce, mandami il log dell'errore: la prima compilazione può richiedere un giro di correzioni.

## 4. Pagina privacy (GitHub Pages)
1. Modifica `docs/privacy.html`: sostituisci `{{EMAIL}}` e `{{INDIRIZZO}}` (l'indirizzo va mostrato; usa quello che vuoi rendere pubblico).
2. Repository → **Settings → Pages** → Branch `main`, cartella `/docs`. Il link sarà `https://fede99311-rgb.github.io/poppy/privacy.html`: è già quello usato nell'app (`privacy_url` in `strings.xml`). Per usare Pages su un repo privato serve un piano GitHub che lo consenta: altrimenti rendi pubblico solo il repo `poppy` (nel repo non ci sono segreti, ma sì la chiave API Firebase, che è pubblica per natura).

## 5. Play Console
1. Account sviluppatore (25 $ una tantum, verifica identità). Account personale: serve **test chiuso con 12 tester per 14 giorni** prima della produzione.
2. Crea app → carica `poppy-aab` in un test chiuso → aggiungi i tester (email Gmail).
3. **Play App Signing**: dopo il primo upload, in *Integrità dell'app → Firma* copia lo **SHA-1 della chiave di firma dell'app** e aggiungilo in Firebase (Impostazioni progetto → la tua app → Aggiungi impronta). Senza questo, il login Google non funziona nella versione scaricata dallo store.
4. Compila scheda (vedi `store/listing.md`): icona `store/icon-512.png`, grafica `store/feature-1024x500.png`, screenshot, categoria, privacy URL, Data safety, classificazione contenuti, pubblico 18+.
5. Crea i 3 prodotti in-app `tip_small`, `tip_medium`, `tip_large` (consumabili) per le mance.
6. Per l'UE (DSA) Google chiede nome e indirizzo del "trader" e un telefono/email di contatto, che compaiono nello store.

## Note
- Limite di lettura: l'app scarica gli ultimi 30 giorni di registrazioni (l'esportazione CSV prende tutto).
- Costi Firebase: Spark è gratuito (50k letture e 20k scritture al giorno, 1 GB). Per una famiglia sono briciole.
- Cambio nome pacchetto: `it.venturini.poppy` in `app/build.gradle.kts` e nel namespace; deve coincidere in Firebase.


## Novità v2
- Aggiunti: pannolini, bagnetto, rumori bianchi, vitamina D/farmaci, febbre, crescita, svezzamento, tappe, grafici, promemoria, widget, blocco biometrico, report PDF, backup/ripristino JSON.
- Nessuna nuova configurazione Firebase: le regole `firestore.rules` valgono per tutti i tipi di voce.
- Su Android 13+ l'app chiede il permesso notifiche quando attivi un promemoria.
- Ricorda di aggiornare la scheda *Sicurezza dei dati* in Play Console: dichiara anche dati sulla salute (facoltativi).
