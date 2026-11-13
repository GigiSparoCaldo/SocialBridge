package com.akemi.socialbridge;

import org.json.JSONObject;

public class JsScripts {
    public static String igFeed() {
        return "(async function(){try{"
            + "const ck=document.cookie.match(/csrftoken=([^;]+)/);const csrf=ck?ck[1]:'';"
            + "const r=await fetch('/api/v1/feed/timeline/',{method:'POST',headers:{'X-IG-App-ID':'936619743392459','X-CSRFToken':csrf,'X-Requested-With':'XMLHttpRequest','Content-Type':'application/x-www-form-urlencoded'},body:'reason=cold_start_fetch&is_pull_to_refresh=0'});"
            + "if(r.ok){const j=await r.json();const out=[];"
            + "(j.feed_items||[]).forEach(x=>{const m=x.media_or_ad;if(!m||!m.user)return;"
            + "let img='',vid='';"
            + "if(m.image_versions2&&m.image_versions2.candidates&&m.image_versions2.candidates.length)img=m.image_versions2.candidates[m.image_versions2.candidates.length-1].url;"
            + "else if(m.carousel_media&&m.carousel_media.length&&m.carousel_media[0].image_versions2)img=m.carousel_media[0].image_versions2.candidates[0].url;"
            + "if(m.video_versions&&m.video_versions.length)vid=m.video_versions[m.video_versions.length-1].url;"
            + "out.push({id:String(m.pk||m.id||''),user:m.user.username||'',avatar:m.user.profile_pic_url||'',text:(m.caption&&m.caption.text)||'',likes:m.like_count||0,comments:m.comment_count||0,img:img,vid:vid});});"
            + "if(out.length)return JSON.stringify({ok:true,items:out.slice(0,18)});}"
            + "}catch(e){}"
            + "const arts=[...document.querySelectorAll('article')].slice(0,15).map((a,i)=>{"
            + "const u=(a.querySelector('a[href^=\"/\"]')||{}).innerText||'utente';"
            + "const im=(a.querySelector('img[src*=\"cdninstagram\"],img[src*=\"fbcdn\"]')||{}).src||'';"
            + "const vd=(a.querySelector('video')||{}).src||'';"
            + "return {id:'dom_'+i,user:u.split('\\n')[0],avatar:'',text:(a.innerText||'').slice(0,220),likes:0,comments:0,img:im,vid:vd};});"
            + "return JSON.stringify({ok:true,items:arts});})();";
    }

    public static String igStories() {
        return "(async function(){try{"
            + "const r=await fetch('/api/v1/feed/reels_tray/',{headers:{'X-IG-App-ID':'936619743392459','X-Requested-With':'XMLHttpRequest'}});"
            + "const j=await r.json();const out=(j.tray||[]).slice(0,16).map(t=>({id:String(t.id||''),user:(t.user&&t.user.username)||'',avatar:(t.user&&t.user.profile_pic_url)||''}));"
            + "return JSON.stringify({ok:true,stories:out});}catch(e){return JSON.stringify({ok:false,err:String(e)});}})();";
    }

    public static String igStoryMedia(String reelId) {
        return "(async function(){try{"
            + "const r=await fetch('/api/v1/feed/reels_media/?reel_ids='+encodeURIComponent(" + JSONObject.quote(reelId) + "),{headers:{'X-IG-App-ID':'936619743392459','X-Requested-With':'XMLHttpRequest'}});"
            + "const j=await r.json();const reel=(j.reels_media&&j.reels_media[0])||{};const items=(reel.items||[]).map(m=>({"
            + "img:(m.image_versions2&&m.image_versions2.candidates&&m.image_versions2.candidates[0].url)||'',"
            + "vid:(m.video_versions&&m.video_versions.length&&m.video_versions[m.video_versions.length-1].url)||''}));"
            + "return JSON.stringify({ok:true,items:items});}catch(e){return JSON.stringify({ok:false,err:String(e)});}})();";
    }

    public static String igLike(String mediaId) {
        return "(async function(){try{"
            + "const ck=document.cookie.match(/csrftoken=([^;]+)/);const csrf=ck?ck[1]:'';"
            + "const r=await fetch('/api/v1/web/likes/'+encodeURIComponent(" + JSONObject.quote(mediaId) + ")+'/like/',{method:'POST',headers:{'X-IG-App-ID':'936619743392459','X-CSRFToken':csrf,'X-Requested-With':'XMLHttpRequest'}});"
            + "return JSON.stringify({ok:r.ok});}catch(e){return JSON.stringify({ok:false});}})();";
    }

    public static String igDmInbox() {
        return "(async function(){try{"
            + "const r=await fetch('/api/v1/direct_v2/inbox/?limit=15',{headers:{'X-IG-App-ID':'936619743392459','X-Requested-With':'XMLHttpRequest'}});"
            + "const j=await r.json();const out=((j.inbox&&j.inbox.threads)||[]).map(t=>({"
            + "id:t.thread_id||'',title:t.thread_title||((t.users&&t.users[0]&&t.users[0].username)||'Chat'),"
            + "last:(t.last_permanent_item&&t.last_permanent_item.text)||'[Media]'}));"
            + "return JSON.stringify({ok:true,threads:out});}catch(e){return JSON.stringify({ok:false,err:String(e)});}})();";
    }

    public static String igDmSend(String threadId, String text) {
        return "(async function(){try{"
            + "const ck=document.cookie.match(/csrftoken=([^;]+)/);const csrf=ck?ck[1]:'';"
            + "const body='action=send_item&thread_ids='+encodeURIComponent('['+JSON.stringify(" + JSONObject.quote(threadId) + ")+']')+'&text='+encodeURIComponent(" + JSONObject.quote(text) + ");"
            + "const r=await fetch('/api/v1/direct_v2/threads/broadcast/text/',{method:'POST',headers:{'X-IG-App-ID':'936619743392459','X-CSRFToken':csrf,'X-Requested-With':'XMLHttpRequest','Content-Type':'application/x-www-form-urlencoded'},body:body});"
            + "return JSON.stringify({ok:r.ok});}catch(e){return JSON.stringify({ok:false});}})();";
    }

    public static String redditFeed(String sub, String sort) {
        String path = (sub == null || sub.isEmpty()) ? "/.json?limit=20&raw_json=1"
                : ("day".equals(sort) || "week".equals(sort) || "month".equals(sort))
                ? "/r/" + sub + "/top.json?t=" + sort + "&limit=20&raw_json=1"
                : "/r/" + sub + "/hot.json?limit=20&raw_json=1";
        return "(async function(){try{"
            + "const r=await fetch(" + JSONObject.quote(path) + ",{credentials:'include'});"
            + "const j=await r.json();return JSON.stringify(j);}catch(e){return JSON.stringify({error:String(e)});}})();";
    }

    public static String redditComments(String permalink) {
        String clean = permalink.replaceAll("^https?://[^/]+", "").split("\\?")[0].replaceAll("/$", "") + ".json?limit=30&depth=3&raw_json=1";
        return "(async function(){try{"
            + "const r=await fetch(" + JSONObject.quote(clean) + ",{credentials:'include'});"
            + "const j=await r.json();return JSON.stringify(j);}catch(e){return JSON.stringify({error:String(e)});}})();";
    }
}
