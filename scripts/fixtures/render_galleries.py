"""Regenerate blind HTML/contact sheets and separate provisional answer pages offline."""
import argparse
import csv
import html
from pathlib import Path
from fixture_tools import ROOT, PLAYLISTS, read_json

STYLE='''body{font:16px/1.5 system-ui,sans-serif;background:#f4f3ef;color:#212b2c;margin:0;padding:32px}main{max-width:1400px;margin:auto}h1{font-size:30px;margin:0 0 12px}.notice{padding:16px;background:#fff1d3;border-left:4px solid #b08036;margin:20px 0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:18px}figure{margin:0;background:white;padding:12px;border:1px solid #dce1df;border-radius:8px}img{display:block;width:100%;height:240px;object-fit:contain;background:#eceeea}figcaption{padding-top:10px}.id{font-family:monospace;font-weight:bold}a{color:#216052}small{display:block;color:#555}table{border-collapse:collapse;width:100%;background:white}td,th{padding:12px;border:1px solid #dce1df;text-align:left}.badge{background:#edf3ed;padding:3px 8px;border-radius:4px}footer{margin-top:32px;color:#555}'''


def page(title, body):
    return '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'+html.escape(title)+'</title><style>'+STYLE+'</style><main>'+body+'</main></html>\n'


def render(root):
    from PIL import Image,ImageDraw,ImageFont
    root=Path(root)
    assets={a['asset_id']:a for a in read_json(root/'manifest.json')['assets']}
    with (root/'evaluation/expected-relevance.csv').open(encoding='utf8',newline='') as f:labels=list(csv.DictReader(f))
    links=[]
    for n,group in enumerate(PLAYLISTS,1):
        case=read_json(root/f'candidates/case_{n:02}.json')
        records={r['photo_id']:r for r in labels if r['playlist_id']==group}
        cards=[];answers=[]
        sheet=Image.new('RGB',(1600,1200),'#f4f3ef');draw=ImageDraw.Draw(sheet)
        font=ImageFont.load_default(size=20)
        for position,photo in enumerate(case['photos']):
            asset=assets[photo['asset_id']];aid=asset['asset_id'];row=records[aid]
            visual=f'<a href="../{asset["path"]}"><img loading="lazy" src="../{asset["path"]}" alt="{aid}"></a><figcaption><span class="id">{aid}</span> · <a href="{html.escape(asset["source_page"],quote=True)}">Source / 来源</a>'
            cards.append('<figure>'+visual+'</figcaption></figure>')
            answers.append('<figure>'+visual+f'<p><span class="badge">{html.escape(row["role"])} · {row["expected_relevance"]}/3</span></p><p>{html.escape(row["reason"])}</p><small>provisional · {html.escape(row["label_basis"])}</small></figcaption></figure>')
            with Image.open(root/asset['path']) as im:
                im.thumbnail((304,260));x=(position%5)*320;y=(position//5)*300
                sheet.paste(im,(x+(320-im.width)//2,y+(260-im.height)//2));draw.text((x+10,y+267),aid,fill='#212b2c',font=font)
        blind=f'<h1>Case {n:02} · 20 photos</h1><p>盲版：只显示中性编号与来源入口。点击图片查看完整测试副本。</p><p><a href="index.html">全部图库</a> · <a href="{group}.jpg">Contact sheet</a> · <a href="../ATTRIBUTION.md">完整署名</a></p><div class="grid">'+''.join(cards)+'</div><footer>图片各自的许可和作者见来源入口与 ATTRIBUTION.md。此页不包含预期分数；正式推理使用 candidates/case_XX.json。</footer>'
        answer=f'<h1>{group} · 初始评价标签</h1><div class="notice">全部标签均为 agent 初选的 provisional 标签。未经过真人试听或审核，不是 ground truth。此目录、答案页和来源描述不得输入推荐器。</div><p><a href="../galleries/{group}.html">盲版</a> · <a href="expected-relevance.csv">完整标注 CSV</a></p><div class="grid">'+''.join(answers)+'</div>'
        (root/f'galleries/{group}.html').write_text(page(f'Case {n:02} blind gallery',blind),encoding='utf8')
        (root/f'evaluation/{group}.html').write_text(page(group+' provisional labels',answer),encoding='utf8')
        sheet.save(root/f'galleries/{group}.jpg','JPEG',quality=88,optimize=True)
        links.append(f'<tr><td>Case {n:02}</td><td>{group}</td><td><a href="{group}.html">盲版图库</a> · <a href="{group}.jpg">Contact sheet</a></td><td><a href="../evaluation/{group}.html">独立答案页</a></td></tr>')
    index='<h1>Music → photo fixtures v1</h1><p>4 个混合候选库，每库 20 个候选。图片文件共享存储，图库不是 80 张全球唯一照片。</p><div class="notice">本页用于人查看。推荐器只能读取导出的中性 input.json、音频和图片。</div><table><tr><th>中性案例</th><th>评价分组</th><th>混排预览</th><th>评价材料</th></tr>'+''.join(links)+'</table><p><a href="../README.md">使用与验证说明</a> · <a href="../ATTRIBUTION.md">完整来源署名</a></p>'
    (root/'galleries/index.html').write_text(page('Music photo fixture galleries',index),encoding='utf8')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT)
    render(parser.parse_args().root)
