package com.akemi.socialbridge;

import org.json.JSONObject;

public class JsScripts {
    public static String igFeed() {
        return "(async function(){let res={ok:true,items:[]};try{"
            + "const ck=document.cookie.match(/csrftoken=([^;]+)/);const csrf=ck?ck[1]:'';"
            + "const r=await fetch('/api/v1/feed/timeline/',{method:'POST',credentials:'include',headers:{'X-IG-App-ID':'936619743392459','X-ASBD-ID':'129477','X-CSRFToken':csrf,'X-Requested-With':'XMLHttpRequest','Content-Type':'application/x-www-form-urlencoded'},body:'reason=cold_start_fetch&is_pull_to_refresh=0'});"
            + "if(r.ok){const j=await r.json();"
            + "(j.feed_items||[]).forEach(x=>{const m=x.media_or_ad;if(!m||!m.user)return;"
            + "let img='',vid='';"
            + "if(m.image_versions2&&m.image_versions2.candidates&&m.image_versions2.candidates.length)img=m.image_versions2.candidates[0].url;"
            + "else if(m.carousel_media&&m.carousel_media.length&&m.carousel_media[0].image_versions2)img=m.carousel_media[0].image_versions2.candidates[0].url;"
            + "if(m.video_versions&&m.video_versions.length)vid=m.video_versions[m.video_versions.length-1].url;"
            + "res.items.push({id:String(m.pk||m.id||''),user:m.user.username||'',avatar:m.user.profile_pic_url||'',text:(m.caption&&m.caption.text)||'',likes:m.like_count||0,comments:m.comment_count||0,img:img,vid:vid});});}"
            + "if(!res.items.length){"
            + "res.items=[...document.querySelectorAll('article')].slice(0,15).map((a,i)=>{"
            + "const u=(a.querySelector('a[href^=\"/\"]')||{}).textContent||'utente';"
            + "const imgs=[...a.querySelectorAll('img')].filter(im=>im.src&&im.width>120);"
            + "const im=imgs.length?imgs[0].src:'';"
            + "const vd=(a.querySelector('video')||{}).src||'';"
            + "return {id:'dom_'+i,user:u.trim().split(/\\s+/)[0],avatar:'',text:(a.textContent||'').slice(0,220),likes:0,comments:0,img:im,vid:vd};});}"
            + "}catch(e){res.err=String(e);}"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igStories() {
        return "(async function(){let res={ok:true,stories:[]};try{"
            + "const r=await fetch('/api/v1/feed/reels_tray/',{credentials:'include',headers:{'X-IG-App-ID':'936619743392459','X-ASBD-ID':'129477','X-Requested-With':'XMLHttpRequest'}});"
            + "const j=await r.json();res.stories=(j.tray||[]).slice(0,16).map(t=>({id:String(t.id||''),user:(t.user&&t.user.username)||'',avatar:(t.user&&t.user.profile_pic_url)||''}));"
            + "}catch(e){res.ok=false;}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igStoryMedia(String reelId) {
        return "(async function(){let res={ok:true,items:[]};try{"
            + "const r=await fetch('/api/v1/feed/reels_media/?reel_ids='+encodeURIComponent(" + JSONObject.quote(reelId) + "),{credentials:'include',headers:{'X-IG-App-ID':'936619743392459','X-Requested-With':'XMLHttpRequest'}});"
            + "const j=await r.json();const reel=(j.reels_media&&j.reels_media[0])||{};res.items=(reel.items||[]).map(m=>({"
            + "img:(m.image_versions2&&m.image_versions2.candidates&&m.image_versions2.candidates[0].url)||'',"
            + "vid:(m.video_versions&&m.video_versions.length&&m.video_versions[m.video_versions.length-1].url)||''}));"
            + "}catch(e){res.ok=false;}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igLike(String mediaId) {
        return "(async function(){let ok=false;try{"
            + "const ck=document.cookie.match(/csrftoken=([^;]+)/);const csrf=ck?ck[1]:'';"
            + "const r=await fetch('/api/v1/web/likes/'+encodeURIComponent(" + JSONObject.quote(mediaId) + ")+'/like/',{method:'POST',credentials:'include',headers:{'X-IG-App-ID':'936619743392459','X-CSRFToken':csrf,'X-Requested-With':'XMLHttpRequest'}});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String igDmInbox() {
        return "(async function(){let res={ok:true,threads:[]};try{"
            + "const r=await fetch('/api/v1/direct_v2/inbox/?limit=15',{credentials:'include',headers:{'X-IG-App-ID':'936619743392459','X-Requested-With':'XMLHttpRequest'}});"
            + "const j=await r.json();res.threads=((j.inbox&&j.inbox.threads)||[]).map(t=>({"
            + "id:t.thread_id||'',title:t.thread_title||((t.users&&t.users[0]&&t.users[0].username)||'Chat'),"
            + "last:(t.last_permanent_item&&t.last_permanent_item.text)||'[Media]'}));"
            + "}catch(e){res.ok=false;}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igDmSend(String threadId, String text) {
        return "(async function(){let ok=false;try{"
            + "const ck=document.cookie.match(/csrftoken=([^;]+)/);const csrf=ck?ck[1]:'';"
            + "const body='action=send_item&thread_ids='+encodeURIComponent('['+JSON.stringify(" + JSONObject.quote(threadId) + ")+']')+'&text='+encodeURIComponent(" + JSONObject.quote(text) + ");"
            + "const r=await fetch('/api/v1/direct_v2/threads/broadcast/text/',{method:'POST',credentials:'include',headers:{'X-IG-App-ID':'936619743392459','X-CSRFToken':csrf,'X-Requested-With':'XMLHttpRequest','Content-Type':'application/x-www-form-urlencoded'},body:body});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String redditFeed(String sub, String sort) {
        String cleanSub = (sub == null) ? "" : sub.trim();
        String path = cleanSub.isEmpty() ? "/.json?limit=20&raw_json=1"
                : ("day".equals(sort) || "week".equals(sort) || "month".equals(sort))
                ? "/r/" + cleanSub + "/top/.json?t=" + sort + "&limit=20&raw_json=1"
                : "/r/" + cleanSub + "/hot/.json?limit=20&raw_json=1";
        return "(async function(){let out=null;try{"
            + "const r=await fetch(" + JSONObject.quote(path) + ",{credentials:'include'});"
            + "const j=await r.json();if(j&&j.data&&j.data.children)out=j;"
            + "}catch(e){}"
            + "if(!out){const sp=[...document.querySelectorAll('shreddit-post')];"
            + "const ch=sp.slice(0,20).map(p=>{const im=p.querySelector('img[src*=\"redd.it\"],img[src*=\"redditmedia\"]');"
            + "return {data:{title:p.getAttribute('post-title')||'',author:p.getAttribute('author')||'',"
            + "subreddit:(p.getAttribute('subreddit-prefixed-name')||'').replace(/^r\\//,''),"
            + "score:parseInt(p.getAttribute('score')||'0',10)||0,"
            + "num_comments:parseInt(p.getAttribute('comment-count')||'0',10)||0,"
            + "permalink:p.getAttribute('permalink')||'',"
            + "url:im?im.src:(p.getAttribute('content-href')||'')}};});"
            + "out={data:{children:ch}};}"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify(out));})();";
    }

    public static String redditComments(String permalink) {
        String clean = permalink.replaceAll("^https?://[^/]+", "").split("\\?")[0].replaceAll("/$", "") + "/.json?limit=30&depth=3&raw_json=1";
        return "(async function(){let out='[]';try{"
            + "const r=await fetch(" + JSONObject.quote(clean) + ",{credentials:'include'});"
            + "out=JSON.stringify(await r.json());}catch(e){}"
            + "AndroidBridge.done('__REQ_ID__',out);})();";
    }

    public static String googleNewsFeed() {
        return "(function(){const out=[],seen=new Set();try{"
            + "document.querySelectorAll('a[href*=\"/read/\"],a[href*=\"/articles/\"]').forEach(a=>{"
            + "const t=(a.getAttribute('aria-label')||a.textContent||'').trim().replace(/\\s+/g,' ');"
            + "if(t.length<20||seen.has(t))return;seen.add(t);"
            + "const box=a.closest('article,c-wiz,div');"
            + "const im=box?(box.querySelector('img[src^=\"http\"]')||{}).src||'':'';"
            + "out.push({title:t,url:a.href||'',img:im,src:'Google News'});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,items:out.slice(0,22)}));})();";
    }
}
