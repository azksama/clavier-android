# Confidentialité

Clavier Android, version alpha — 29 septembre 2026.

## Texte et audio

Le clavier transmet les touches à l’application où vous écrivez, par le mécanisme Android `InputConnection`. Il ne conserve pas d’historique général de frappe. Ses complétions proviennent d’une courte liste de mots intégrée ; elles n’apprennent pas vos messages.

La traduction Google ML Kit, la correction facultative Gemini Nano/AICore et la transcription Whisper s’exécutent sur l’appareil. Traduction et correction lisent la sélection ou, sans sélection, un champ complet limité à 4 000 caractères (180 pour la correction). Elles proposent un résultat à relire. Le remplacement exige une action explicite et est refusé si le champ ou le texte a changé. La dictée est limitée à 60 secondes ; l’audio est gardé en mémoire, jamais enregistré dans un fichier, et ses buffers de travail sont effacés après traitement.

Le microphone est demandé depuis les réglages à votre initiative et ouvert pendant la dictée uniquement. Quitter le clavier interrompt l’enregistrement et demande l’annulation de la transcription.

## Réseau et fournisseurs

- Les modèles de traduction sont téléchargés auprès de Google. [Traitement des données par ML Kit](https://developers.google.com/ml-kit/terms) : les SDK peuvent transmettre des données techniques d’appareil et d’usage, indépendamment du texte traité localement.
- La correction dépend de Google AICore, de sa disponibilité et de ses propres téléchargements. Elle est facultative. Son téléchargement explicite peut utiliser le réseau mobile.
- Whisper Tiny est téléchargé depuis Hugging Face, avec révision, taille et SHA-256 vérifiés. Whisper ne nécessite aucune clé OpenAI.
- Tenor reçoit la clé fournie par l’utilisateur, les mots recherchés dans le panneau GIF, la langue/pays, l’adresse IP et les requêtes de médias ; le partage d’un GIF est signalé à Tenor. Le texte du champ de message n’est pas envoyé à Tenor. [Confidentialité Google](https://policies.google.com/privacy).
- Les liens vers GitHub ouvrent le navigateur de l’utilisateur.

Clavier n’ajoute pas de publicité, d’outil d’analyse d’audience ni de serveur de collecte. Une connexion à ChatGPT ou l’utilisation d’un abonnement ChatGPT ne sont pas intégrées.

## Presse-papier et secrets

L’historique du presse-papier est désactivé par défaut. Si vous l’activez, Clavier mémorise jusqu’à 20 extraits de texte de 4 000 caractères. Il les chiffre avec AES-GCM et une clé Android Keystore. Les extraits ordinaires expirent après une heure ; ils sont filtrés et purgés à la prochaine consultation, puis toutes les 30 secondes pendant l’affichage du panneau. Un extrait épinglé reste jusqu’à suppression. Désactiver l’historique le supprime. Effacer l’historique ne modifie pas le presse-papier système.

Les contenus portant le drapeau sensible Android sont ignorés. Cette protection dépend du signal fourni par l’application d’origine : ne copiez pas de secrets dans l’historique si leur origine ne signale pas leur caractère sensible. La clé Tenor est également chiffrée dans le Keystore et peut être supprimée dans les réglages.

Les préférences de thème ne sont pas sensibles et restent dans les préférences locales. Les sauvegardes Android et les transferts d’appareil sont exclus. Les modèles restent sur ce téléphone ; les packs ML Kit et le modèle Whisper peuvent être supprimés dans les réglages. Les modèles AICore sont gérés par Android.

Les GIF insérés sont temporairement stockés dans le cache. Seul le fichier choisi est partagé avec l’application de destination via une autorisation de lecture Android. Les fichiers de plus de 24 heures sont purgés lors du prochain téléchargement ; Android ou la désinstallation peuvent vider le cache.

## Mots de passe

Dans les champs déclarés comme mot de passe, ou demandant l’absence d’apprentissage personnalisé, Clavier désactive ses suggestions lexicales, ses outils IA et l’accès à son historique du presse-papier. Cette détection repose sur les informations de l’application hôte ; un champ incorrectement déclaré peut ne pas être reconnu.

Les suggestions intégrées de remplissage automatique Android 11+ sont des vues fournies par le système et le gestionnaire de mots de passe. Clavier héberge ces vues sans inspecter, journaliser ou stocker leur contenu. Le remplissage est déclenché par votre sélection. Le menu système Android reste le mécanisme de repli lorsque les suggestions intégrées ne sont pas disponibles. [Fonctionnement officiel](https://developer.android.com/identity/autofill/ime-autofill).

## Suppression et contact

Effacer les données Android de Clavier ou désinstaller l’application supprime ses données locales. Cela ne supprime pas les messages déjà insérés dans d’autres applications ni les données détenues par les fournisseurs. Signalez les problèmes de confidentialité dans le dépôt, sans publier de mots de passe, de clés API ou de messages privés : [GitHub](https://github.com/azksama/clavier-android/issues).
