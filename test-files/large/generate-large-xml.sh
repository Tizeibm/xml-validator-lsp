#!/bin/bash
# =============================================================================
# Script de génération de fichiers XML volumineux pour tester le streaming
# Usage: ./generate-large-xml.sh [nombre_items] [fichier_sortie]
# Exemple: ./generate-large-xml.sh 1000000 ../large/bookstore-1M.xml
# =============================================================================

set -e

COUNT=${1:-100000}
OUTPUT=${2:-"../large/bookstore-large.xml"}

echo "Génération de $COUNT livres dans $OUTPUT..."

# Calculate approximate size
APPROX_SIZE=$((COUNT * 500))  # ~500 bytes per book entry
if [ $APPROX_SIZE -gt 1073741824 ]; then
    echo "Taille approximative: $(echo "scale=2; $APPROX_SIZE / 1073741824" | bc) GB"
elif [ $APPROX_SIZE -gt 1048576 ]; then
    echo "Taille approximative: $(echo "scale=2; $APPROX_SIZE / 1048576" | bc) MB"
else
    echo "Taille approximative: $(echo "scale=2; $APPROX_SIZE / 1024" | bc) KB"
fi

GENRES=("Fiction" "Non-Fiction" "Science" "Technology" "History" "Biography" "Poetry" "Children")
CURRENCIES=("EUR" "USD" "GBP" "XAF")

# Write header
cat > "$OUTPUT" << 'HEADER'
<?xml version="1.0" encoding="UTF-8"?>
<bookstore name="Mega Bookstore - Stress Test">
HEADER

# Generate books
for ((i=1; i<=COUNT; i++)); do
    GENRE=${GENRES[$((RANDOM % ${#GENRES[@]}))]}
    CURRENCY=${CURRENCIES[$((RANDOM % ${#CURRENCIES[@]}))]}
    YEAR=$((1900 + RANDOM % 126))
    PRICE=$(echo "scale=2; $((RANDOM % 10000)) / 100" | bc)
    
    cat >> "$OUTPUT" << EOF
    <book id="$i" lang="fr">
        <title>Livre de Test Numéro $i - $GENRE</title>
        <author>
            <firstName>Auteur$((RANDOM % 100))</firstName>
            <lastName>Nom$((RANDOM % 200))</lastName>
            <nationality>Pays$((RANDOM % 50))</nationality>
        </author>
        <isbn>978-$((RANDOM % 9))-$((10000 + RANDOM % 89999))-$((1000 + RANDOM % 8999))-$((RANDOM % 9))</isbn>
        <price currency="$CURRENCY">$PRICE</price>
        <year>$YEAR</year>
        <genre>$GENRE</genre>
        <description>Description du livre numéro $i. Ceci est un texte de test pour remplir le fichier XML avec du contenu réaliste et tester le streaming.</description>
    </book>
EOF

    # Progress indicator every 10000 items
    if [ $((i % 10000)) -eq 0 ]; then
        echo "  ... $i / $COUNT livres générés"
    fi
done

# Write footer
echo "</bookstore>" >> "$OUTPUT"

# Show file size
FILE_SIZE=$(du -h "$OUTPUT" | cut -f1)
echo ""
echo "✅ Fichier généré: $OUTPUT"
echo "📏 Taille: $FILE_SIZE"
echo "📚 Nombre de livres: $COUNT"
