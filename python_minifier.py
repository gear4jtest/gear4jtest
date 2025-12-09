import os
import re

def minify_java_code(code):
    code = re.sub(r'/\*.*?\*/', '', code, flags=re.DOTALL)  # Commentaires /* */
    code = re.sub(r'//.*', '', code)                        # Commentaires //
    lines = [line.strip() for line in code.splitlines() if line.strip()]
    code = ' '.join(lines)
    code = re.sub(r'\s*([{}();=,+\-*/<>])\s*', r'\1', code)
    return code

def minify_all_java_files(input_folder, output_folder):
    for root, dirs, files in os.walk(input_folder):
        for file in files:
            if file.endswith(".java"):
                input_path = os.path.join(root, file)
                relative_path = os.path.relpath(input_path, input_folder)
                output_path = os.path.join(output_folder, relative_path)

                # Créer le dossier de sortie s'il n'existe pas
                os.makedirs(os.path.dirname(output_path), exist_ok=True)

                with open(input_path, "r", encoding="utf-8") as f:
                    java_code = f.read()

                minified_code = minify_java_code(java_code)

                with open(output_path, "w", encoding="utf-8") as f:
                    f.write(minified_code)

                print(f"Minifié : {input_path} → {output_path}")

if __name__ == "__main__":
    input_folder = "/tmp"       # Dossier source à minifier
    output_folder = "minified"       # Dossier de sortie

    minify_all_java_files(input_folder, output_folder)
    print("✅ Minification terminée.")

