# Maquette du clavier Android

Document éditable : `Clavier-Android.pen`. Les captures PNG sont dans `exports/`.

## Rendu actuel

- `exports/x3r0nJ.png` : clavier AZERTY clair.
- `exports/udfIq.png` : clavier AZERTY sombre.
- `exports/rzeN9.png` : dictée Whisper en cours.
- `exports/SdDx4.png` : panneau émojis.
- `exports/JEFjb.png` : aperçu du panneau GIF.
- `exports/A1YtrB.png` : proposition de correction.

## Couverture

30 écrans : saisie claire et sombre, émojis, GIF, presse-papier, outils, traduction, correction et annulation, écoute/transcription/résultat Whisper, activation Android, réglages, téléchargement du modèle, langues, apparence, fournisseurs, permission micro et refus, absence de parole, historique vide, indisponibilité Tenor et incompatibilité de correction locale, collection de thèmes, clavier verre, clavier néomorphique, éditeur de couleurs, sélecteur HEX et téléchargement d'une langue ML Kit.

Le canvas contient aussi une présentation et neuf composants réutilisables dans deux planches de fondations. Le document utilise des variables de thème, des instances de composants et des calques nommés.

## Thèmes

- `exports/mHAHd.png` : collection classique, verre et relief.
- `exports/r5gr6l.png` : clavier glassmorphique Lagune.
- `exports/r5Rhhk.png` : clavier néomorphique Porcelaine.
- `exports/Dket9.png` : réglage du fond, des touches, du texte et de l'accent.
- `exports/GcKvj.png` : sélecteur de teinte, saturation, luminosité, palette et HEX.
- `exports/Q7HLT.png` : téléchargement préalable d'une langue ML Kit.

Les effets de verre utilisent le fond interne du clavier. Ils ne capturent pas le contenu de l'application hôte. Les dimensions et les positions des touches restent identiques entre les thèmes.

## Limites de cette livraison

Maquette statique, pas une application Android. Les conversations sont fictives. Les quatre vignettes du panneau GIF sont des photos Unsplash d'illustration obtenues avec pen.dev, pas des résultats Tenor ni des GIF animés. Le rendu des émojis sur le canvas est monochrome ; l'application devra utiliser les glyphes emoji couleur du système Android.

La traduction est confirmée sur Google ML Kit, localement après téléchargement des langues. Le fournisseur de correction reste à choisir ; les réglages de correction locale et distante représentent des parcours proposés. La disponibilité réelle de Gemini Nano doit être vérifiée sur le téléphone. Aucun compte, clé API ou service distant n'a été connecté. Les variantes paysage, une main et agrandissement du texte restent à détailler avant l'implémentation.

La vérification structurelle pen.dev a été effectuée après recalcul des layouts ; aucun débordement n'a été remonté sur les 30 écrans au dernier contrôle. Les rendus représentatifs ont été inspectés. Aucun test Android n'a été réalisé.

## Sources vérifiées le 29 septembre 2026

- [Authentification OpenAI](https://learn.chatgpt.com/docs/auth) : pas de flux de connexion ChatGPT pour clavier tiers établi par cette documentation.
- [ML Kit Translation](https://developers.google.com/ml-kit/language/translation) : traduction locale après téléchargement des langues ; limites sur les traductions complexes.
- [ML Kit Proofreading](https://developers.google.com/ml-kit/genai/proofreading/android) : français, textes courts, bêta et compatibilité à vérifier.
- [LanguageTool auto-hébergé](https://dev.languagetool.org/http-server).
- [DeepL Write API](https://developers.deepl.com/docs/translate/write-quickstart).
- [Tenor](https://developers.google.com/tenor/guides/quickstart) : plus de nouveaux clients API depuis janvier 2026.
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) : moteur proposé pour intégrer Whisper au téléphone.
