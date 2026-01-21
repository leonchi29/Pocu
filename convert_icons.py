#!/usr/bin/env python3
"""
Script para convertir archivos .ico de Pocu a PNG en múltiples tamaños para Android
"""

import os
from PIL import Image
import sys

def create_android_icons(ico_path, output_prefix, is_dark=False):
    """
    Convierte un archivo .ico a PNGs en todos los tamaños necesarios para Android

    Tamaños estándar:
    - ldpi: 36x36
    - mdpi: 48x48
    - hdpi: 72x72
    - xhdpi: 96x96
    - xxhdpi: 144x144
    - xxxhdpi: 192x192
    """

    if not os.path.exists(ico_path):
        print(f"❌ Archivo no encontrado: {ico_path}")
        return False

    try:
        print(f"\n📂 Abriendo: {ico_path}")
        img = Image.open(ico_path)
        print(f"✅ Formato original: {img.format}, Tamaño: {img.size}")

        # Convertir RGBA si es necesario
        if img.mode in ('RGBA', 'LA'):
            # Crear fondo blanco o negro según tema
            background = Image.new('RGB', img.size, (255, 255, 255) if not is_dark else (0, 0, 0))
            background.paste(img, mask=img.split()[-1] if img.mode == 'RGBA' else None)
            img = background
        elif img.mode != 'RGB':
            img = img.convert('RGB')

        # Definir tamaños y carpetas destino
        sizes = {
            'ldpi': 36,
            'mdpi': 48,
            'hdpi': 72,
            'xhdpi': 96,
            'xxhdpi': 144,
            'xxxhdpi': 192
        }

        base_path = 'C:/Users/herma/AndroidStudioProjects/Pocu2/app/src/main/res'
        theme_suffix = '_dark' if is_dark else ''

        success_count = 0
        for dpi, size in sizes.items():
            try:
                # Redimensionar
                resized = img.resize((size, size), Image.Resampling.LANCZOS)

                # Crear carpeta si no existe
                output_dir = f'{base_path}/mipmap-{dpi}'
                os.makedirs(output_dir, exist_ok=True)

                # Guardar PNG
                output_file = f'{output_dir}/ic_launcher{theme_suffix}.png'
                resized.save(output_file, 'PNG', quality=95)
                print(f"  ✅ {dpi:8} ({size:3}x{size:3}) → {output_file}")
                success_count += 1
            except Exception as e:
                print(f"  ❌ {dpi}: {e}")

        return success_count == len(sizes)

    except Exception as e:
        print(f"❌ Error procesando {ico_path}: {e}")
        return False

def main():
    print("=" * 70)
    print("🎨 CONVERTIDOR DE LOGOS ICO A PNG PARA ANDROID - POCU")
    print("=" * 70)

    project_root = "C:/Users/herma/AndroidStudioProjects/Pocu2"

    # Procesar logo claro
    print("\n🌞 Procesando logo CLARO (Pocu.ico.ico)...")
    light_ico = f"{project_root}/Pocu.ico.ico"
    create_android_icons(light_ico, 'ic_launcher', is_dark=False)

    # Procesar logo oscuro
    print("\n🌙 Procesando logo OSCURO (pocu-dark.ico)...")
    dark_ico = f"{project_root}/pocu-dark.ico"
    create_android_icons(dark_ico, 'ic_launcher', is_dark=True)

    print("\n" + "=" * 70)
    print("✅ CONVERSIÓN COMPLETADA")
    print("=" * 70)
    print("\n📁 Archivos generados en:")
    print("   └─ app/src/main/res/mipmap-*/ic_launcher.png (claro)")
    print("   └─ app/src/main/res/mipmap-*/ic_launcher_dark.png (oscuro)")
    print("\n💡 Los logos se cambiarán automáticamente según el tema del dispositivo")
    print("=" * 70)

if __name__ == "__main__":
    main()
