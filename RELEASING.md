# Distribution

Le dépôt est public. Les premières versions sont marquées **prerelease** jusqu'à validation sur appareils réels.

La clé de signature de la première alpha est conservée exclusivement sur le poste de construction dans `.signing/release.jks`. Les identifiants sont dans `.signing/keystore.properties`. Ce dossier est exclu de Git et des archives de sources. **Conserver une sauvegarde privée de ces deux fichiers avant de supprimer le workspace** : la même clé est nécessaire pour mettre à jour l'installation existante. Ils ne doivent jamais devenir des assets de release.

Format du fichier privé pour une nouvelle installation de construction :

```properties
storeFile=release.jks
keyAlias=clavier
storePassword=VOTRE_SECRET_LOCAL
keyPassword=VOTRE_SECRET_LOCAL
```

`./gradlew :app:assembleRelease` crée `app/build/outputs/apk/release/app-release.apk` si la signature est configurée, sinon un APK **non signé à ne pas distribuer**. Mettre à jour `versionCode` et `versionName` pour les versions suivantes.

Avant chaque publication :

1. Compiler, exécuter les tests JVM et Lint. Pour les tests Android, suivre le workflow ARTEMIS et documenter l'appareil réellement utilisé.
2. Vérifier avec `apksigner verify --verbose --print-certs` et `zipalign -c -P 16 -v 4` depuis Android build-tools ; vérifier les segments ELF des bibliothèques ARM64/x86_64.
3. Préparer APK, archive des maquettes et SHA256SUMS. GitHub fournit les archives ZIP/tar.gz des sources du tag.
4. Créer la prerelease avec une note décrivant fonctions, limites et validations.
5. Télécharger chaque asset publié et comparer sa taille et son SHA-256 aux fichiers locaux. La publication n'est terminée qu'après ce contrôle.

Les archives automatiques des sources n'incluent pas le checkout Whisper ignoré ; `scripts/bootstrap.py` récupère sa révision exacte. Le modèle de transcription est téléchargé séparément par l'application avec une vérification SHA-256.
