# Renders the Play Store icon (512x512) and feature graphic (1024x500) as SVG from the launcher icon vectors.
# Usage: python3 tools/play/render_assets.py <dir>, then render with ImageMagick (see dashboard-app/art/play/README.md).
import re,sys
out=sys.argv[1]
s=open('dashboard-app/src/main/res/drawable-v24/ic_launcher_foreground.xml').read()
paths=re.findall(r'<path\b(.*?)(?:/>|</path>)',s,re.S)
logo=''
for p in paths:
    d=re.search(r'pathData="([^"]+)"',p).group(1)
    if 'gradient' in p:
        logo+=f'<path d="{d}" fill="url(#fade)"/>'
    else:
        logo+=f'<path d="{d}" fill="#F4F9FE" fill-rule="evenodd"/>'
defs='''<defs>
<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#35BAFC"/><stop offset="1" stop-color="#0B6BF8"/></linearGradient>
<linearGradient id="fade" gradientUnits="userSpaceOnUse" x1="161" y1="0" x2="440" y2="0">
<stop offset="0" stop-color="#F4F9FE" stop-opacity="0.10"/><stop offset="0.1" stop-color="#F4F9FE" stop-opacity="0.28"/>
<stop offset="0.32" stop-color="#F4F9FE" stop-opacity="0.53"/><stop offset="0.53" stop-color="#F4F9FE" stop-opacity="0.83"/>
<stop offset="0.68" stop-color="#F4F9FE" stop-opacity="0.90"/><stop offset="1" stop-color="#F4F9FE" stop-opacity="1"/></linearGradient>
</defs>'''
# Play icon: the adaptive icon's visible 72x72 of 108, full bleed (Play rounds the corners itself)
icon=f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="18 18 72 72">{defs}
<rect x="0" y="0" width="108" height="108" fill="url(#bg)"/>
<g transform="translate(17.75 17.634) scale(0.058)">{logo}</g></svg>'''
open(out+'/icon.svg','w').write(icon)
# Feature graphic 1024x500: logo (926x629 artwork bounds) and name, the tagline under
lw=300; sc=lw/926; lh=629*sc
lx=110; ly=(500-lh)/2-28
feat=f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">{defs}
<rect width="1024" height="500" fill="url(#bg)"/>
<g transform="translate({lx} {ly}) scale({sc}) translate(-162 -313)">{logo}</g>
<text x="{lx+lw+36}" y="{ly+lh*0.66}" font-family="Noto Sans" font-weight="700" font-size="112" fill="#FFFFFF">HADash</text>
<text x="512" y="{ly+lh+78}" text-anchor="middle" font-family="Noto Sans" font-weight="500" font-size="38" fill="#FFFFFF" fill-opacity="0.92">Your Home Assistant dashboards, instantly</text>
</svg>'''
open(out+'/feature.svg','w').write(feat)
