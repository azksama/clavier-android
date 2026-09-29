# Validation de la version alpha

## Vérifications sur le poste de construction

- 30 maquettes pen.dev : contrôles de débordement sans problème signalé ; export PNG 824 × 1784 et PDF 30 pages.
- Compilation Kotlin/Compose et native whisper.cpp pour ARM64 et x86_64.
- 9 tests JVM réussis : suppression Unicode (accents, drapeaux, modificateurs de teinte, séquences ZWJ, keycaps et tags), détection des champs sensibles, couleurs HEX et contraste.
- Android Lint : 0 erreur, 20 avertissements (versions de dépendances, suggestions KTX et attribut Autofill ignoré avant Android 11). Aucun avertissement supprimé par une baseline.
- Signature APK v2 / RSA 4096 vérifiée, ZIP et les huit bibliothèques natives alignés sur 16 Kio. Certificat SHA-256 : `2433ff0d14ec4085e1421ba119d133bdb5a8a0cbf025de55358d572247b6457f`.
- SHA-256 des fichiers publiés vérifié après téléchargement de la release.

Les résultats de CI sont accessibles dans [GitHub Actions](https://github.com/azksama/clavier-android/actions).

## Non vérifié sur appareil

Aucun appareil ni émulateur Android autorisé n’était connecté pendant cette construction. Ces vérifications ne prouvent donc ni l’installation, ni le rendu Compose, ni les interactions de l’IME en situation réelle. Les captures publiées sont des maquettes pen.dev et ne sont pas des captures d’exécution de l’APK.

À valider avec ARTEMIS et un appareil choisi :

1. Activation, saisie AZERTY/QWERTY, appuis longs, effacement, actions envoyer/rechercher/suivant et changement d’application.
2. Thèmes clair/sombre/personnalisés, rotation, écrans étroits, grands caractères et TalkBack.
3. Traduction avec et sans modèles, hors ligne, annulation, texte modifié pendant le traitement et remplacement/annulation.
4. Permissions microphone, enregistrement, arrêt à 60 secondes, interruption et transcription réelle. Mesurer latence, RAM, chauffe et qualité sur le matériel visé.
5. Disponibilité AICore, téléchargement et correction. Cette API bêta ne fonctionne que sur des configurations compatibles.
6. Recherche et insertion Tenor avec une clé existante valide. Sans clé fournie, aucun appel Tenor authentifié n’a été validé.
7. Autofill avec Bitwarden, 1Password et autres fournisseurs souhaités, dans une application native et un navigateur : suggestions, déverrouillage, sélection, changement de champ et disparition des anciennes suggestions. Aucune compatibilité par marque n’est encore certifiée.
8. Historique chiffré, expiration, suppression, exclusion des contenus sensibles et champs de mot de passe.

Pas de saisie par glissement, de mode une main ni de dictionnaire prédictif comparable à Gboard dans cette première alpha. Les complétions intégrées sont une courte liste FR/EN ; la correction est une action explicite avec aperçu, pas une autocorrection universelle.
