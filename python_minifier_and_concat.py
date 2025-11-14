import os
import re

def minify_java_code(code):
    code = re.sub(r'/\*.*?\*/', '', code, flags=re.DOTALL)  # Commentaires /* */
    code = re.sub(r'//.*', '', code)                        # Commentaires //
    # Supprimer les instructions package
    code = re.sub(r'^\s*package\s+.*?;', '', code, flags=re.MULTILINE)
    # Supprimer les instructions import
    code = re.sub(r'^\s*import\s+.*?;', '', code, flags=re.MULTILINE)
    lines = [line.strip() for line in code.splitlines() if line.strip()]
    code = ' '.join(lines)
    code = re.sub(r'\s*([{}();=,+\-*/<>])\s*', r'\1', code)
    return code

def minify_and_concatenate(input_folder, output_file):
    all_minified = []

    for root, _, files in os.walk(input_folder):
        for file in files:
            if file.endswith(".java"):
                input_path = os.path.join(root, file)
                with open(input_path, "r", encoding="utf-8") as f:
                    java_code = f.read()
                minified = minify_java_code(java_code)

                # Ajoute un commentaire indiquant le fichier d'origine (optionnel mais utile)
                header = f"\n// --- {os.path.relpath(input_path, input_folder)} ---\n"
                all_minified.append(header + minified)

                print(f"Minifié : {input_path}")

    # Écriture du fichier final concaténé
    with open(output_file, "w", encoding="utf-8") as f:
        f.write('\n'.join(all_minified))

    print(f"\n✅ Tous les fichiers Java ont été minifiés et concaténés dans : {output_file}")

if __name__ == "__main__":
    input_folder = "gear4jtest-xml/src/main/java/io/test/gear4test/xml/visitor"           # Dossier d'entrée
    output_file = "/tmp/bundle.min.java"          # Fichier de sortie unique

    minify_and_concatenate(input_folder, output_file)

