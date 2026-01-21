#!/usr/bin/env python3
"""
Convertir .ico a PNG usando PIL/Pillow
Requiere: pip install Pillow
"""

from PIL import Image
import os
import sys

def resize_and_save(ico_path, output_dir, filename, sizes):
    """
    Abre un .ico y lo redimensiona a múltiples tamaños
    """
    try:
        img = Image.open(ico_path)
        print(f"\n✅ Abierto: {ico_path}")
        print(f"   Formato: {img.format}, Tamaño: {img.size}")

        # Convertir a RGB si es necesario
        if img.mode in ('RGBA', 'LA', 'P'):
            # Crear fondo blanco
            background = Image.new('RGB', img.size, (255, 255, 255))
            if img.mode == 'P':
                img = img.convert('RGBA')
            background.paste(img, mask=img.split()[-1] if img.mode == 'RGBA' else None)
            img = background
        elif img.mode != 'RGB':
            img = img.convert('RGB')

        # Guardar en múltiples tamaños
        for dpi, size in sizes.items():
            resized = img.resize((size, size), Image.Resampling.LANCZOS)

            # Crear carpeta si no existe
            folder = os.path.join(output_dir, f'mipmap-{dpi}')
            os.makedirs(folder, exist_ok=True)

            # Guardar
            output_path = os.path.join(folder, filename)
            resized.save(output_path, 'PNG', quality=95)
            print(f"   ✅ {dpi:8} ({size:3}x{size:3}) → {output_path}")

        return True
    except Exception as e:
        print(f"   ❌ Error: {e}")
        return False

def main():
    print("=" * 80)
    print("🎨 CONVERTIDOR ICO A PNG - POCU")
    print("=" * 80)

    project_root = r"C:\Users\herma\AndroidStudioProjects\Pocu2"
    res_dir = os.path.join(project_root, "app", "src", "main", "res")

    # Tamaños para Android
    sizes = {
        'ldpi': 36,
        'mdpi': 48,
        'hdpi': 72,
        'xhdpi': 96,
        'xxhdpi': 144,
        'xxxhdpi': 192
    }

    # Logos a procesar
    logos = [
        {
            'path': os.path.join(project_root, 'Pocu.ico.ico'),
            'filename': 'ic_launcher.png',
            'name': 'Logo CLARO'
        },
        {
            'path': os.path.join(project_root, 'pocu-dark.ico'),
            'filename': 'ic_launcher.png',
            'name': 'Logo OSCURO',
            'subfolder': 'drawable-night'
        }
    ]

    for logo in logos:
        print(f"\n🌟 Procesando: {logo['name']}")

        ico_path = logo['path']
        if not os.path.exists(ico_path):
            print(f"   ❌ NO ENCONTRADO: {ico_path}")
            continue

        output_dir = res_dir
        if 'subfolder' in logo:
            # Para logo oscuro, no usar mipmap, solo guardar como drawable
            output_dir = os.path.join(res_dir, logo['subfolder'])

        resize_and_save(ico_path, output_dir, logo['filename'], sizes)

    print("\n" + "=" * 80)
    print("✅ CONVERSIÓN COMPLETADA")
    print("=" * 80)

if __name__ == "__main__":
    main()
