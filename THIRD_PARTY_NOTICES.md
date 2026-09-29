# Composants tiers

Le code original de Clavier est sous licence MIT. Les composants ci-dessous conservent leurs licences ; les marques Google, Gboard, Tenor, OpenAI et Whisper appartiennent à leurs titulaires. Ce projet n’est affilié à aucun d’eux.

| Composant | Version | Licence / conditions |
| --- | --- | --- |
| whisper.cpp / ggml | v1.8.7, commit 48f628a84833905ee4a0658ee6d4a5c915ce1997 | MIT, copie dans `app/src/main/assets/licenses/whisper-MIT.txt` |
| Modèle Whisper Tiny multilingue | conversion ggerganov, révision 5359861c739e955e79d9a303bcbc70fb988958b1 | MIT, [Whisper](https://github.com/openai/whisper/blob/main/LICENSE), téléchargement séparé |
| Kotlin, coroutines, AndroidX/Compose | versions fixées dans Gradle | Apache-2.0 |
| OkHttp / Okio | OkHttp 4.12.0 | Apache-2.0 |
| Coil | 2.7.0 | Apache-2.0 |
| Google ML Kit / Google Play services / AICore | traduction 17.0.3, proofreading 1.0.0-beta1 | [Conditions ML Kit](https://developers.google.com/ml-kit/terms), composants propriétaires et leurs notices transitives |
| Tenor | API v2 | [Conditions Tenor](https://developers.google.com/tenor/terms), clé existante requise |
| C++ runtime / LLVM | NDK 29.0.14206865 | Apache-2.0 avec exception LLVM et notices du NDK, copie dans les assets |

Les métadonnées de licences des dépendances ne sont pas supprimées de l’APK. Les quatre vignettes de GIF des maquettes sont des images statiques Unsplash, référencées dans le fichier pen.dev ; elles ne sont pas intégrées à l’application ni soumises à la licence MIT du projet. Aucun fichier audio ou modèle de reconnaissance n’est embarqué dans l’APK.
