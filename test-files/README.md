# Fichiers de Test XML/XSD

Ce dossier contient des fichiers de test pour valider le fonctionnement du **Mass XML Validator LSP**.

## Structure

```
test-files/
├── schemas/                    # Schémas XSD
│   ├── bookstore.xsd           # Librairie (types complexes, énumérations, restrictions)
│   ├── employees.xsd           # Employés (key/keyref, choice, contraintes d'unicité)
│   ├── orders.xsd              # Commandes (namespaces, union, list, mixed content)
│   └── simple.xsd              # Schéma minimal pour tests rapides
│
├── valid/                      # XML valides (doivent passer la validation)
│   ├── bookstore-valid.xml     # ✅ Librairie avec 5 livres
│   ├── employees-valid.xml     # ✅ Entreprise avec 4 employés
│   ├── orders-valid.xml        # ✅ 3 commandes avec namespaces
│   └── simple-valid.xml        # ✅ 3 items simples
│
├── invalid/                    # XML invalides (doivent échouer avec erreurs précises)
│   ├── bookstore-invalid.xml   # ❌ 9+ erreurs (types, attributs, énumérations)
│   ├── employees-invalid.xml   # ❌ 10+ erreurs (keyref, choice, contraintes)
│   ├── orders-invalid.xml      # ❌ 9+ erreurs (namespace, types, manquants)
│   ├── simple-invalid.xml      # ❌ 5 erreurs (attributs, types, ordre)
│   ├── malformed.xml           # ❌ XML mal formé (erreurs de parsing)
│   └── empty-root.xml          # ❌ Racine vide (minOccurs violation)
│
└── large/                      # Fichiers volumineux pour stress testing
    └── generate-large-xml.sh   # Script de génération
```

## Paires Schema ↔ XML

| Schéma XSD | XML Valide | XML Invalide |
|------------|------------|--------------|
| `schemas/bookstore.xsd` | `valid/bookstore-valid.xml` | `invalid/bookstore-invalid.xml` |
| `schemas/employees.xsd` | `valid/employees-valid.xml` | `invalid/employees-invalid.xml` |
| `schemas/orders.xsd` | `valid/orders-valid.xml` | `invalid/orders-invalid.xml` |
| `schemas/simple.xsd` | `valid/simple-valid.xml` | `invalid/simple-invalid.xml` |

## Types d'Erreurs Testées

### Erreurs de Schéma
- Attributs requis manquants (`use="required"`)
- Types de données invalides (string au lieu d'integer, etc.)
- Valeurs hors limites (`minInclusive`, `maxInclusive`)
- Valeurs d'énumération invalides
- Patterns non respectés (ISBN, email, SKU)
- Éléments manquants ou en ordre incorrect
- Violations `xs:choice` (plusieurs branches choisies)
- Violations `xs:unique` et `xs:key/keyref`
- Erreurs de namespace

### Erreurs de Parsing
- Tags non fermés
- Tags mal assortis (`<name>...</title>`)
- Caractères invalides dans les noms d'éléments
- XML mal formé

### Cas Limites
- Élément racine vide
- Valeurs numériques extrêmes
- Chaînes vides
- Contenu mixte (mixed content)
- Types union et list

## Génération de Gros Fichiers

Pour tester le streaming avec des fichiers volumineux :

```bash
cd test-files/large

# Générer 100K livres (~50 MB)
chmod +x generate-large-xml.sh
./generate-large-xml.sh 100000 bookstore-100K.xml

# Générer 1M livres (~500 MB)
./generate-large-xml.sh 1000000 bookstore-1M.xml

# Générer 10M livres (~5 GB)
./generate-large-xml.sh 10000000 bookstore-10M.xml
```

Les fichiers générés sont valides contre `schemas/bookstore.xsd`.
