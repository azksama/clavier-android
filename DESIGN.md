# Direction visuelle du clavier

La référence explicitement demandée est Gboard. Conserver ses repères de saisie et la grammaire Material Android : panneau inférieur stable, rangée de suggestions, barre d'outils compacte, touches AZERTY arrondies, barre d'espace ample et touche d'action accentuée.

## Surfaces

- Clavier et outils intégrés : mode Operate ; la tâche de saisie reste prioritaire.
- Configuration : listes Material, groupes clairement titrés et actions explicites.
- Document pen.dev : composants réutilisables en haut, scénarios et états en dessous.

## Système

- Roboto, lettres des touches 23 sp, corps 14–16 sp, titres 20–28 sp.
- Clair : surface #F8F9FA, panneau #E8EAED, touches #FFFFFF, texte #202124, secondaire #5F6368.
- Sombre : surface #151719, panneau #202124, touches #3C4043, texte #F1F3F4, secondaire #BDC1C6.
- Accent Material bleu, conteneur bleu pâle ; aucun logo Google ni prétention à être Gboard.
- Icônes Material Symbols Rounded cohérentes. Émojis réservés au contenu du sélecteur.
- Pas de navigation flottante de type iOS. Barre de geste Android et respect des zones système.

## Comportements à conserver lors de l'implémentation

- Suggestions en ligne ; outils secondaires remplacent le panneau sans masquer le texte source.
- Aperçu avant remplacement pour traduction et correction, action Annuler après application.
- Whisper : téléchargement initial, permission micro, écoute, traitement, aperçu, erreur et absence de parole.
- Presse-papier : activation volontaire, épinglage, suppression et durée de conservation visible.
- Champs sensibles : pas de suggestions contextuelles, de presse-papier ou de traitement distant.
- Les fournisseurs sont nommés dans les réglages ; les fonctions distantes annoncent la destination du texte.
- Variantes paysage, une main, grand texte et thème sombre.

## Thèmes personnalisables

Demande explicite : conserver le style classique et ajouter glassmorphisme et neumorphisme. Personnaliser le fond, les touches, le texte et l'accent avec aperçu. Le glassmorphisme utilise le fond interne du clavier, sans lire ni flouter le contenu de l'application hôte. Le neumorphisme utilise des ombres douces opposées tout en maintenant la lisibilité des légendes et des états appuyés.

La traduction est confirmée sur Google ML Kit ; la correction reste à choisir.

## Validation

Vérifier les débordements dans pen.dev, inspecter les rendus, puis exporter les vues utiles. La validation d'une maquette ne vaut pas validation sur téléphone.
