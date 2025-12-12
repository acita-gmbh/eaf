# Nutzungserfassung und Abrechnung

**Wie wir aus technischen Daten Rechnungen machen.**

Dieses Dokument erklärt das Konzept der verbrauchsbasierten Abrechnung in DCM. Es richtet sich an Leser aus dem Bereich **Finanzbuchhaltung und Controlling**.

---

## Das Grundprinzip: „Pay-as-you-go“

Traditionelle IT-Kosten sind oft Pauschalen („Flat Rates“). Das ist einfach, aber oft unfair. Wenn eine Abteilung einen großen Server nur für 3 Stunden benötigt, zahlt sie oft für den ganzen Monat.

DCM nutzt ein **verbrauchsabhängiges Modell** (ähnlich wie Ihre Stromrechnung). Wir messen exakt, wie lange eine Ressource belegt war.

### Was wir messen (Die Währung)

Wir berechnen keine „Stückzahlen“, sondern **Leistung x Zeit**:

*   **vCPU-Stunden:** Ein virtueller Prozessorkern, der eine Stunde lang reserviert ist.
*   **GB-Stunden:** Ein Gigabyte Arbeitsspeicher (RAM) oder Festplatte, das eine Stunde lang belegt ist.

**Beispielrechnung:**
Eine Projektgruppe mietet einen „Large Server“ (4 Prozessoren, 16 GB RAM) für genau 24 Stunden.
*   Prozessoren: `4 Kerne * 24 Stunden = 96 vCPU-Stunden`
*   Speicher: `16 GB * 24 Stunden = 384 GB-Stunden`

Am Ende des Monats werden diese Einheiten mit einem Preis multipliziert (z. B. 0,05 € pro vCPU-Stunde).

---

## Wie die Erfassung funktioniert

Das System arbeitet wie eine Stoppuhr.

1.  **Start:** Sobald ein Mitarbeiter auf „Erstellen“ klickt und der Server bereitsteht, startet die Uhr.
2.  **Stopp:** Sobald der Server „gelöscht“ oder „archiviert“ wird, stoppt die Uhr.
3.  **Änderung:** Wenn der Server vergrößert wird (z. B. von 8GB auf 16GB RAM), stoppen wir die alte Uhr und starten eine neue mit den höheren Werten.

**Wichtig:** Auch ausgeschaltete Server verursachen Kosten (z. B. für den belegten Speicherplatz auf der Festplatte), auch wenn sie keine Rechenleistung verbrauchen. Das liegt daran, dass virtuelle Maschinen auch im ausgeschalteten Zustand weiterhin Speicherressourcen (wie Festplattenplatz) belegen und diese Ressourcen Kosten verursachen. Unser System kann das unterscheiden.

## Preismodelle

Wir können für jeden Mandanten (Kunden/Abteilung) individuelle Preismodelle hinterlegen:

1.  **Pauschal:** 50€ pro Server/Monat (einfach).
2.  **Exakt:** Cent-genaue Abrechnung nach Stunden (fair).
3.  **Gestaffelt:** Die ersten 100 GB sind frei, danach kostenpflichtig.

## Die Rechnung/Kostenverrechnung

Am Monatsende (oder Quartalsende) generiert das System einen Bericht:

*   **Wer:** Welche Abteilung / Welches Projekt?
*   **Was:** Summe der verbrauchten Stunden.
*   **Wieviel:** Der errechnete Euro-Betrag.

Dieser Bericht kann als **PDF-Rechnung** exportiert werden oder als Daten-Datei direkt an Ihr ERP-System (z. B. SAP, NetSuite) zur internen Leistungsverrechnung übergeben werden.

## Ihr Vorteil

*   **Transparenz:** Sie sehen genau, welche Abteilung das IT-Budget verbraucht („Showback“).
*   **Kostenkontrolle:** Mitarbeiter gehen sparsamer mit Ressourcen um, wenn sie wissen, dass die Uhr läuft. „Leichen“ (vergessene Server) werden schneller gelöscht.
*   **Fairness:** Projekte zahlen nur für das, was sie wirklich nutzen.
