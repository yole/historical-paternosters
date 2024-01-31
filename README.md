## Historical Pater Nosters Collection

In the Early Modern period, the most common way to provide a sample of a foreign language
was a translation of the [Lord's Prayer](https://en.wikipedia.org/wiki/Lord%27s_Prayer), also
known as Pater Noster or Oratio Dominica. Multiple books were released containing translations
of Pater Noster into many languages (up to 100 and even more), and travel reports often contained
translations of Pater Noster into the languages of the lands the author traveled to.

While many of those translations are in well-known languages and are not interesting from the
linguistic point of view, and others are badly corrupted or sometimes even made up, there are
quite a few which represent the earliest written attestations of the corresponding languages,
or demonstrate unique features of extinct dialects. And while some of those texts have been
subject to detailed historical and linguistic analysis, others don't seem to have attracted
the attention they deserve. The phenomenon of those collections as a whole doesn't seem to be
well-researched either.

The collections have a complicated textual history. Collectors of Pater Noster translations
copy texts from each other, often specifying their sources (sometimes incorrectly), and often
introducing errors in the process of copying. The languages of each translation are also
sometimes misidentified.

The goal of this project is to create a database of the Pater Noster translations published
in collections that came out before 1800. For each translation, this database aims to:
 * identify the language;
 * track down the original source wherever possible;
 * specify the transmission history;
 * annotate the differences between the versions of the same translation published by
different authors;
 * provide interlinear glosses;
 * provide links to articles where a given translation is discussed or analyzed.

The effort is currently actively in progress.

### Methodology

* Differences between different versions of a translation are annotated as footnotes.
* Differences in punctuation and diacritics are not annotated.
* Punctuation is normalized so that every petition is a new sentence.
* A text in a non-Latin script and its transliteration are presented as two different specimens.
  The transliteration is marked as 'based on' the original text.
* Differences between transliterations of the same text are not annotated; only one version
  is selected as canonical.
* Purely graphical differences ('i' vs. 'j', 'u' vs. 'v', 'w' vs 'vv', 'ss' vs 'ß') are not annotated.
* Glosses are provided only when they are present in an original attestation.

### Bibliography

The references covering individual books or texts are provided in the pages for these books or texts.
The following references cover the phenomenon of Lord's Prayer collections in general:

 * Schmidt-Riese, Roland (2003): Ordnung nach Babylon. Frühzeitliche Spracheninventare in Frankreich
   und Deutschland. In: Sammeln, Ordnen, Veranschaulichen: Zum Wissenskompilatorik in der Frühen Neuzeit.
   Frank Büttner, Markus Friedrich, Helmut Zedelmaier (eds.) Lit Verlag Münster
 * Trabant, Jürgen (1998): [Mithridates: De Gesner jusqu'à Adelung et Vater](https://www.jstor.org/stable/27758558). 
   In: Cahiers Ferdinand de Saussure, No. 51, pp. 95-111.

[//]: # (End of index.html)

### This Repository

This repository contains the database of the specimens themselves (the `data` directory),
as well as code to generate a [public Web site](https://paternosters.yole.page/) from the database.

If you want to add something to the database or to correct an error, you're very much welcome
to file an issue or a pull request.
