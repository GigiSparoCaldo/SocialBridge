package com.akemi.socialbridge;

import org.json.JSONObject;

public class JsScripts {
    private static String igHdrs(boolean post) {
        return "const ck=document.cookie.match(/csrftoken=([^;]+)/);const csrf=ck?ck[1]:'';"
            + "const cl=sessionStorage.getItem('www-claim-v2')||'0';"
            + "const h={'X-IG-App-ID':'936619743392459','X-ASBD-ID':'129477','X-IG-WWW-Claim':cl,'X-CSRFToken':csrf,'X-Requested-With':'XMLHttpRequest'"
            + (post ? ",'Content-Type':'application/x-www-form-urlencoded'" : "") + "};";
    }

    public static String igFeed() {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(true)
            + "const r=await fetch('/api/v1/feed/timeline/',{method:'POST',credentials:'include',headers:h,body:'reason=cold_start_fetch&is_pull_to_refresh=0'});"
            + "if(r.ok){const j=await r.json();"
            + "(j.feed_items||[]).forEach(x=>{const m=x.media_or_ad;if(!m||!m.user)return;"
            + "let imgs=[],vid='';"
            + "if(m.carousel_media&&m.carousel_media.length){m.carousel_media.forEach(cm=>{"
            + "if(cm.image_versions2&&cm.image_versions2.candidates&&cm.image_versions2.candidates.length)imgs.push(cm.image_versions2.candidates[0].url);"
            + "if(!vid&&cm.video_versions&&cm.video_versions.length)vid=cm.video_versions[cm.video_versions.length-1].url;});}"
            + "else if(m.image_versions2&&m.image_versions2.candidates&&m.image_versions2.candidates.length)imgs.push(m.image_versions2.candidates[0].url);"
            + "if(!vid&&m.video_versions&&m.video_versions.length)vid=m.video_versions[m.video_versions.length-1].url;"
            + "res.items.push({id:String(m.pk||m.id||''),user:m.user.username||'',text:(m.caption&&m.caption.text)||'',likes:m.like_count||0,comments:m.comment_count||0,img:imgs[0]||'',imgs:imgs,vid:vid});});}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igStories() {
        return "(async function(){let res={ok:true,stories:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/feed/reels_tray/',{credentials:'include',headers:h});"
            + "const j=await r.json();res.stories=(j.tray||[]).slice(0,18).map(t=>({"
            + "id:String(t.id||(t.user&&t.user.pk)||''),user:(t.user&&t.user.username)||'',avatar:(t.user&&t.user.profile_pic_url)||''}));"
            + "}catch(e){res.ok=false;}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igStoryMedia(String reelId) {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/feed/reels_media/?reel_ids='+encodeURIComponent(" + JSONObject.quote(reelId) + "),{credentials:'include',headers:h});"
            + "const j=await r.json();const reel=(j.reels_media&&j.reels_media[0])||(j.reels&&j.reels[" + JSONObject.quote(reelId) + "])||{};"
            + "res.items=(reel.items||[]).map(m=>({"
            + "img:(m.image_versions2&&m.image_versions2.candidates&&m.image_versions2.candidates[0].url)||'',"
            + "vid:(m.video_versions&&m.video_versions.length&&m.video_versions[m.video_versions.length-1].url)||''}));"
            + "}catch(e){res.ok=false;}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igComments(String mediaId) {
        return "(async function(){let res={ok:true,comments:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/media/'+encodeURIComponent(" + JSONObject.quote(mediaId) + ")+'/comments/?can_support_threading=true',{credentials:'include',headers:h});"
            + "const j=await r.json();const mapC=c=>{"
            + "let gif='';if(c.giphy_media_info&&c.giphy_media_info.images&&c.giphy_media_info.images.fixed_height)gif=c.giphy_media_info.images.fixed_height.url;"
            + "else if(c.image_versions2&&c.image_versions2.candidates&&c.image_versions2.candidates.length)gif=c.image_versions2.candidates[0].url;"
            + "const reps=(c.preview_child_comments||c.child_comments||[]).map(mapC);"
            + "return {user:(c.user&&c.user.username)||'utente',text:c.text||'',likes:c.comment_like_count||0,img:gif,replies:reps};};"
            + "res.comments=(j.comments||[]).slice(0,25).map(mapC);"
            + "}catch(e){res.ok=false;}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igLike(String mediaId) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const r=await fetch('/api/v1/web/likes/'+encodeURIComponent(" + JSONObject.quote(mediaId) + ")+'/like/',{method:'POST',credentials:'include',headers:h});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String igDmInbox() {
        return "(async function(){let res={ok:true,threads:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/direct_v2/inbox/?limit=15',{credentials:'include',headers:h});"
            + "const j=await r.json();res.threads=((j.inbox&&j.inbox.threads)||[]).map(t=>({"
            + "id:t.thread_id||'',title:t.thread_title||((t.users&&t.users[0]&&t.users[0].username)||'Chat'),"
            + "last:(t.last_permanent_item&&t.last_permanent_item.text)||'[Media]'}));"
            + "}catch(e){res.ok=false;}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igDmSend(String threadId, String text) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const body='action=send_item&thread_ids='+encodeURIComponent('['+JSON.stringify(" + JSONObject.quote(threadId) + ")+']')+'&text='+encodeURIComponent(" + JSONObject.quote(text) + ");"
            + "const r=await fetch('/api/v1/direct_v2/threads/broadcast/text/',{method:'POST',credentials:'include',headers:h,body:body});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
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

    public static String geminiListChats() {
        return "(function(){try{"
            + "const menuBtn=document.querySelector('button[aria-label*=\"Menu\"],button[aria-label*=\"menu\"],button[data-test-id=\"side-nav-menu-button\"]');"
            + "if(menuBtn&&document.querySelectorAll('a[href*=\"/app/\"]').length<2)menuBtn.click();"
            + "setTimeout(()=>{"
            + "const seen=new Set(),chats=[];"
            + "document.querySelectorAll('a[href*=\"/app/\"]').forEach(a=>{"
            + "const href=a.href||'';const m=href.match(/\\/app\\/([a-zA-Z0-9_-]+)/);"
            + "if(!m||seen.has(m[1]))return;seen.add(m[1]);"
            + "const title=(a.textContent||'').trim().replace(/\\s+/g,' ');"
            + "if(title.length>1)chats.push({id:m[1],title:title.slice(0,60),url:href});});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,chats:chats.slice(0,25)}));"
            + "},500);}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,chats:[]}));}})();";
    }

    public static String geminiReadThread() {
        return "(function(){try{"
            + "const turns=[],seen=new Set();"
            + "document.querySelectorAll('user-query,model-response,.user-query-text,.model-response-text').forEach(el=>{"
            + "const tag=el.tagName.toLowerCase();const cls=(el.className||'').toString();"
            + "const role=(tag.includes('user')||cls.includes('user'))?'Tu':'Gemini';"
            + "const txt=(el.innerText||'').trim();"
            + "const key=role+':'+txt.slice(0,40);"
            + "if(txt.length>0&&!seen.has(key)){seen.add(key);turns.push({role:role,text:txt});}});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,turns:turns.slice(-20)}));"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,turns:[]}));}})();";
    }

    public static String geminiAsk(String prompt, String model, String think) {
        String prefix = "alto".equals(think)
                ? "[Istruzione: Usa ragionamento approfondito ad alto livello, analizza tutti i dettagli passo dopo passo prima di rispondere]\n"
                : "basso".equals(think)
                ? "[Istruzione: Risposta rapida a basso livello di ragionamento, sii sintetico e vai subito al punto in poche righe]\n"
                : "";
        String fullPrompt = prefix + prompt;
        return "(function(){try{"
            + "const mTarget=" + JSONObject.quote(model) + ";"
            + "if(mTarget==='flash'||mTarget==='pro'){"
            + "const sw=document.querySelector('button[data-test-id=\"bard-mode-menu-button\"],button.gds-mode-switch-button,button[aria-label*=\"modello\"],button[aria-label*=\"model\"]');"
            + "if(sw)sw.click();"
            + "setTimeout(()=>{"
            + "const opts=[...document.querySelectorAll('[role=\"menuitem\"],[role=\"option\"],mat-option,button')];"
            + "const hit=opts.find(o=>{const t=(o.innerText||'').toLowerCase();return mTarget==='pro'?(t.includes('pro')||t.includes('thinking')||t.includes('ragionamento')):(t.includes('flash')||t.includes('veloce'));});"
            + "if(hit)hit.click();"
            + "},250);}"
            + "setTimeout(()=>{"
            + "const ed=document.querySelector('div.ql-editor,[contenteditable=\"true\"],textarea');"
            + "if(!ed){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Apri scheda Gemini su Z Flip e accedi a Google'}));return;}"
            + "ed.focus();ed.textContent='';"
            + "document.execCommand('insertText',false," + JSONObject.quote(fullPrompt) + ");"
            + "if(!ed.textContent)ed.textContent=" + JSONObject.quote(fullPrompt) + ";"
            + "ed.dispatchEvent(new Event('input',{bubbles:true}));"
            + "const prevCnt=document.querySelectorAll('model-response,.model-response-text,message-content').length;"
            + "setTimeout(()=>{"
            + "const btn=document.querySelector('button[aria-label*=\"Invia\"],button[aria-label*=\"Send\"],button.send-button');"
            + "if(btn)btn.click();"
            + "let last='',same=0,tries=0;"
            + "const iv=setInterval(()=>{tries++;"
            + "const nodes=document.querySelectorAll('model-response,.model-response-text,message-content');"
            + "const cur=nodes.length>prevCnt?nodes[nodes.length-1].innerText.trim():(nodes.length?nodes[nodes.length-1].innerText.trim():'');"
            + "if(cur&&cur===last)same++;else{last=cur;same=0;}"
            + "if((same>=2&&cur.length>1)||tries>32){clearInterval(iv);"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,reply:cur||'Nessuna risposta ricevuta.'}));}"
            + "},750);},450);},450);"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:String(e)}));}})();";
    }
}
