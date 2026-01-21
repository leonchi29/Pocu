#!/usr/bin/env python3
"""
Script alternativo para convertir ICO a PNG usando conversión más simple
"""
import os
import subprocess
import sys

def convert_ico_to_png(ico_file, output_png):
    """Convertir ICO a PNG usando ffmpeg o convert"""
    # Intentar con ffmpeg primero
    try:
        subprocess.run([
            'ffmpeg', '-i', ico_file, '-vframes', '1', output_png
        ], capture_output=True, check=True)
        return True
    except:
        pass

    # Intentar con convert (ImageMagick)
    try:
        subprocess.run([
            'convert', ico_file, output_png
        ], capture_output=True, check=True)
        return True
    except:
        pass

    return False

# Ruta del proyecto
project_root = r'C:\Users\herma\AndroidStudioProjects\Pocu2'
ico_claro = os.path.join(project_root, 'Pocu.ico.ico')
ico_oscuro = os.path.join(project_root, 'pocu-dark.ico')

print("Intentando conversión de ICO a PNG...")
print(f"Logo claro: {ico_claro}")
print(f"Logo oscuro: {ico_oscuro}")

if os.path.exists(ico_claro):
    print("✅ Archivo claro encontrado")
else:
    print("❌ Archivo claro NO encontrado")

if os.path.exists(ico_oscuro):
    print("✅ Archivo oscuro encontrado")
else:
    print("❌ Archivo oscuro NO encontrado")
