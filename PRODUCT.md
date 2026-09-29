# Clavier Android

<!-- impeccable:product-schema 1 -->

## Platform

android

## Product Purpose

Créer un clavier système Android pour saisir et dicter ses messages, insérer des émojis et des GIF Tenor, utiliser un presse-papier, traduire et corriger du texte.

## Confirmed Decisions

- Interface proche de Gboard, demandée explicitement par l'utilisateur.
- Concevoir d'abord les écrans dans pen.dev, avant l'implémentation Android.
- « Synthèse vocale » signifie ici dictée : transformer la voix en texte avec Whisper.
- Le fournisseur de GIF demandé est Tenor.
- L'utilisateur étudie les alternatives à l'API OpenAI pour la traduction et la correction.
- Traduction : Google ML Kit, confirmé par l'utilisateur.
- Système de thèmes : classique, glassmorphisme et neumorphisme, avec couleurs personnalisables.
- La direction proche de Gboard a reçu un retour positif de l'utilisateur après visualisation des captures.

## Operating Context

Le clavier apparaît dans le champ de saisie d'une autre application. Les conversations dessinées dans la maquette sont fictives et servent uniquement à montrer ce contexte.

## Capabilities and Constraints

- L'accès par abonnement ChatGPT à un clavier tiers n'est pas établi par la documentation officielle consultée. Ne pas présenter de connexion fictive ou d'accès inclus garanti.
- Google indique que Tenor n'accepte plus de nouveaux clients API depuis janvier 2026. Prévoir l'accès avec une clé existante et un état indisponible avec ouverture du site.
- ML Kit permet la traduction locale après téléchargement des langues.
- ML Kit Proofreading prend en charge le français, mais reste en bêta et dépend de la compatibilité de l'appareil.
- LanguageTool auto-hébergé et DeepL sont des alternatives étudiées, pas encore sélectionnées par l'utilisateur.

## Open Decisions

- Nom définitif du clavier ; « Clavier » est un nom de travail.
- Modèle du téléphone ; fournisseur de correction complémentaire si Gemini Nano est indisponible.
- Hypothèse de maquette : français / AZERTY par défaut, thèmes clair et sombre.

## Evidence on Hand

Une première alpha native est implémentée en Kotlin / Jetpack Compose avec un InputMethodService. Whisper.cpp est compilé en JNI pour ARM64 et x86_64. La correction locale Gemini Nano est facultative et vérifie la compatibilité sur l'appareil. Les suggestions des gestionnaires de mots de passe passent par Android Autofill, avec intégration dans le clavier à partir d'Android 11.

Les écrans pen.dev restent des maquettes, sans preuve de fonctionnement sur appareil. La compilation, les tests JVM et les contrôles d'artefacts sont distingués des essais réels dans VALIDATION.md. Le dépôt GitHub est public et la première distribution est une prerelease alpha.
