# NMEA GPS Server for OpenCPN (Android)

Application Android qui lit un GPS via USB-C (NMEA) et retransmet les phrases NMEA à **OpenCPN** via un serveur TCP local. Conçue pour une mise en service simple : afficher l’IP locale, le nombre de clients et relayer les données NMEA vers OpenCPN.

---

## Table des matières

* [Fonctionnalités](#fonctionnalités)
* [Prérequis](#prérequis)
* [Installation](#installation)
* [Configuration importante (Vendor / Product ID)](#configuration-importante-vendor--product-id)
* [Utilisation](#utilisation)
* [Manifest & permissions](#manifest--permissions)
* [Dépannage rapide](#dépannage-rapide)
* [Contribuer](#contribuer)
* [Licence](#licence)

---

## Fonctionnalités

* Lecture des phrases NMEA depuis un GPS branché en USB-C.
* Serveur TCP local qui diffuse les phrases NMEA (port par défaut `10110`).
* UI : affichage des derniers messages NMEA, logs, IP locale et nombre de clients connectés.
* Support multi-clients TCP.

---

## Prérequis

* Android Studio (version récente).
* Appareil Android supportant OTG / USB Host (USB-C).
* GPS compatible NMEA sur USB (ou adaptateur série -> USB).

---

## Installation

```bash
git clone https://github.com/ton-compte/nmea-gps-server.git
# Ouvrir le projet dans Android Studio, build & run sur l'appareil Android
```

---

## Configuration importante (Vendor / Product ID)

Le code contient une vérification du Vendor ID et Product ID du GPS. **Il faut adapter ces valeurs** pour votre matériel (sinon le device peut être ignoré).

Exemple (fichier `MainActivity.kt`) :

```kotlin
private fun isGpsDevice(device: UsbDevice): Boolean {
    // <-- ADAPTER ICI pour votre GPS
    val targetVendorId = 0x1546     // remplacer par le vendor id de votre GPS
    val targetProductId = 0x01A8    // remplacer par le product id de votre GPS

    return device.vendorId == targetVendorId && device.productId == targetProductId
}
```

Si vous voulez accepter tout device série générique, commentez/ajustez la vérification ou implémentez une liste de VID/PID.

### Modifier le port TCP (optionnel)

Le port par défaut est défini lors de l'instanciation du serveur dans `MainActivity` :

```kotlin
private val tcpServer = NmeaTcpServer(10110) // changer 10110 si besoin
```

---

## Utilisation

1. Brancher le GPS sur l’appareil Android (OTG).
2. Lancer l’application.

   * L’app affiche l’IP locale (à utiliser côté OpenCPN).
   * L’app affiche le nombre de clients TCP connectés.
3. Dans **OpenCPN** : ajouter une connexion réseau TCP vers `IP_locale:10110` (ou le port configuré).
4. Les phrases NMEA apparaitront dans OpenCPN en temps réel.

---

## Manifest & permissions

Vérifier que `AndroidManifest.xml` contient au minimum :

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-feature android:name="android.hardware.usb.host" android:required="false" />
```

(Le permission `INTERNET` est nécessaire pour le serveur TCP local. `usb.host` indique la compatibilité OTG.)

---

## Dépannage rapide

* **Aucun device détecté** : vérifier OTG / câble, autorisations USB (l’app demande la permission au device).
* **Impossible d’afficher l’IP** : vérifier que l’app a accès au réseau et que l’appareil est connecté (Wi-Fi / Ethernet).
* **OpenCPN ne reçoit rien** : vérifier IP affichée dans l’app, port, et que le client OpenCPN pointe vers la même IP/port.
* **Le périphérique est ignoré** : vérifier et changer le VendorID/ProductID dans `isGpsDevice`.
* **Client count ne s’actualise pas** : confirmer que `TextView` avec `@id/clientCountText` existe et que le `Handler` est démarré.

---

## Contribuer

1. Fork du dépôt.
2. Créer une branche `feature/xxx` ou `fix/xxx`.
3. PR avec description des changements.
4. Respecter le format de licence (voir ci-dessous).

---

## Licence

Ce projet est distribué sous **CC BY-NC-SA 4.0** — vous pouvez partager et adapter à des fins **non commerciales**, en créditant l’auteur et en redistribuant sous la même licence.

---
