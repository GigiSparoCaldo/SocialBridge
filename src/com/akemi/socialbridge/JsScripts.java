package com.akemi.socialbridge;

import org.json.JSONObject;

public class JsScripts {
    private static String igHdrs(boolean post) {
        return "const ck=document.cookie.match(/csrftoken=([^;]+)/);const csrf=ck?ck[1]:'';"
            + "const cl=sessionStorage.getItem('www-claim-v2')||'0';"
            + "const h={'X-IG-App-ID':'936619743392459','X-ASBD-ID':'129477','X-IG-WWW-Claim':cl,'X-CSRFToken':csrf,'X-Requested-With':'XMLHttpRequest'"
            + (post ? ",'Content-Type':'application/x-www-form-urlencoded'" : "") + "};";
    }

    private static String mapIgMediaFn() {
        return "const mapM=m=>{if(!m||!m.user)return null;let imgs=[],vid='';"
            + "if(m.carousel_media&&m.carousel_media.length){m.carousel_media.forEach(cm=>{"
            + "if(cm.image_versions2&&cm.image_versions2.candidates&&cm.image_versions2.candidates.length)imgs.push(cm.image_versions2.candidates[0].url);"
            + "if(!vid&&cm.video_versions&&cm.video_versions.length)vid=cm.video_versions[cm.video_versions.length-1].url;});}"
            + "else if(m.image_versions2&&m.image_versions2.candidates&&m.image_versions2.candidates.length)imgs.push(m.image_versions2.candidates[0].url);"
            + "if(!vid&&m.video_versions&&m.video_versions.length)vid=m.video_versions[m.video_versions.length-1].url;"
            + "return {id:String(m.pk||m.id||''),user:m.user.username||'',text:(m.caption&&m.caption.text)||'',likes:m.like_count||0,comments:m.comment_count||0,img:imgs[0]||'',imgs:imgs,vid:vid};};";
    }

    public static String igFeed(String maxId) {
        String body = (maxId == null || maxId.isEmpty())
                ? "'reason=cold_start_fetch&is_pull_to_refresh=0'"
                : "'reason=pagination&max_id='+encodeURIComponent(" + JSONObject.quote(maxId) + ")";
        return "(async function(){let res={ok:true,items:[],next:''};try{" + igHdrs(true) + mapIgMediaFn()
            + "const r=await fetch('/api/v1/feed/timeline/',{method:'POST',credentials:'include',headers:h,body:" + body + "});"
            + "if(r.ok){const j=await r.json();res.next=String(j.next_max_id||'');"
            + "(j.feed_items||[]).forEach(x=>{const it=mapM(x.media_or_ad);if(it)res.items.push(it);});}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igExplore() {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(false) + mapIgMediaFn()
            + "const r=await fetch('/api/v1/discover/web/explore_grid/?include_fixed_destinations=false',{credentials:'include',headers:h});"
            + "const j=await r.json();(j.sectional_items||[]).forEach(s=>{"
            + "const lay=s.layout_content||{};(lay.medias||[]).forEach(w=>{const it=mapM(w.media);if(it)res.items.push(it);});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igSaved() {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(false) + mapIgMediaFn()
            + "const r=await fetch('/api/v1/feed/saved/posts/',{credentials:'include',headers:h});"
            + "const j=await r.json();(j.items||[]).forEach(w=>{const it=mapM(w.media);if(it)res.items.push(it);});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igStories() {
        return "(async function(){let res={ok:true,stories:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/feed/reels_tray/',{credentials:'include',headers:h});"
            + "const j=await r.json();(j.tray||[]).slice(0,22).forEach(t=>{"
            + "const pk=String((t.user&&t.user.pk)||t.id||'');const u=(t.user&&t.user.username)||'';"
            + "if(pk&&u)res.stories.push({id:pk,user:u,avatar:(t.user&&t.user.profile_pic_url)||''});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igStoryMedia(String reelId) {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(false)
            + "const rid=String(" + JSONObject.quote(reelId) + ");"
            + "const r=await fetch('/api/v1/feed/reels_media/?reel_ids='+encodeURIComponent(rid),{credentials:'include',headers:h});"
            + "const j=await r.json();let reel=(j.reels_media&&j.reels_media[0])||(j.reels&&j.reels[rid]);"
            + "if(!reel&&j.reels){const ks=Object.keys(j.reels);if(ks.length)reel=j.reels[ks[0]];}"
            + "((reel&&reel.items)||[]).forEach(m=>{res.items.push({"
            + "img:(m.image_versions2&&m.image_versions2.candidates&&m.image_versions2.candidates[0].url)||'',"
            + "vid:(m.video_versions&&m.video_versions.length&&m.video_versions[m.video_versions.length-1].url)||''});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igProfile(String username) {
        return "(async function(){let res={ok:false,items:[]};try{" + igHdrs(false) + mapIgMediaFn()
            + "const u=" + JSONObject.quote(username) + ";"
            + "const r=await fetch('/api/v1/users/web_profile_info/?username='+encodeURIComponent(u),{credentials:'include',headers:h});"
            + "const j=await r.json();const usr=j.data&&j.data.user;"
            + "if(usr){res.ok=true;res.id=String(usr.id||'');res.user=usr.username||u;res.name=usr.full_name||'';"
            + "res.bio=usr.biography||'';res.avatar=usr.profile_pic_url_hd||usr.profile_pic_url||'';"
            + "res.posts_count=(usr.edge_owner_to_timeline_media&&usr.edge_owner_to_timeline_media.count)||0;"
            + "res.followers=(usr.edge_followed_by&&usr.edge_followed_by.count)||0;"
            + "res.following=(usr.edge_follow&&usr.edge_follow.count)||0;res.followed=!!usr.followed_by_viewer;"
            + "const r2=await fetch('/api/v1/feed/user/'+encodeURIComponent(res.id)+'/?count=18',{credentials:'include',headers:h});"
            + "if(r2.ok){const j2=await r2.json();(j2.items||[]).forEach(m=>{const it=mapM(m);if(it)res.items.push(it);});}}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igFollow(String userId, boolean unfollow) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const r=await fetch('/api/v1/web/friendships/'+encodeURIComponent(" + JSONObject.quote(userId) + ")+'/" + (unfollow ? "unfollow" : "follow") + "/',{method:'POST',credentials:'include',headers:h});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String igSearch(String q) {
        return "(async function(){let res={ok:true,users:[],tags:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/web/search/topsearch/?context=blended&query='+encodeURIComponent(" + JSONObject.quote(q) + "),{credentials:'include',headers:h});"
            + "const j=await r.json();(j.users||[]).slice(0,15).forEach(w=>{const u=w.user||{};res.users.push({user:u.username||'',name:u.full_name||''});});"
            + "(j.hashtags||[]).slice(0,8).forEach(w=>{const t=w.hashtag||{};res.tags.push({name:t.name||'',count:t.media_count||0});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igComments(String mediaId) {
        return "(async function(){let res={ok:true,comments:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/media/'+encodeURIComponent(" + JSONObject.quote(mediaId) + ")+'/comments/?can_support_threading=true',{credentials:'include',headers:h});"
            + "const j=await r.json();const mapC=c=>{let gif='';"
            + "if(c.giphy_media_info&&c.giphy_media_info.images&&c.giphy_media_info.images.fixed_height)gif=c.giphy_media_info.images.fixed_height.url;"
            + "else if(c.image_versions2&&c.image_versions2.candidates&&c.image_versions2.candidates.length)gif=c.image_versions2.candidates[0].url;"
            + "return {user:(c.user&&c.user.username)||'utente',text:c.text||'',likes:c.comment_like_count||0,img:gif,replies:(c.preview_child_comments||c.child_comments||[]).map(mapC)};};"
            + "res.comments=(j.comments||[]).slice(0,25).map(mapC);"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igAddComment(String mediaId, String text) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const r=await fetch('/api/v1/web/comments/'+encodeURIComponent(" + JSONObject.quote(mediaId) + ")+'/add/',{method:'POST',credentials:'include',headers:h,body:'comment_text='+encodeURIComponent(" + JSONObject.quote(text) + ")});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String igLike(String mediaId) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const r=await fetch('/api/v1/web/likes/'+encodeURIComponent(" + JSONObject.quote(mediaId) + ")+'/like/',{method:'POST',credentials:'include',headers:h});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String igDmInbox() {
        return "(async function(){let res={ok:true,threads:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/direct_v2/inbox/?limit=15',{credentials:'include',headers:h});"
            + "const j=await r.json();res.threads=((j.inbox&&j.inbox.threads)||[]).map(t=>({id:t.thread_id||'',title:t.thread_title||((t.users&&t.users[0]&&t.users[0].username)||'Chat'),last:(t.last_permanent_item&&t.last_permanent_item.text)||'[Media]'}));"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igDmThread(String threadId) {
        return "(async function(){let res={ok:true,messages:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/direct_v2/threads/'+encodeURIComponent(" + JSONObject.quote(threadId) + ")+'/?limit=20',{credentials:'include',headers:h});"
            + "const j=await r.json();const th=j.thread||{};const users={};(th.users||[]).forEach(u=>{users[String(u.pk)]=u.username;});"
            + "(th.items||[]).reverse().forEach(it=>{const u=users[String(it.user_id)];res.messages.push({sender:u||'Tu',isMe:!u,text:it.text||'[Media]'});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igDmSend(String threadId, String text) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const body='action=send_item&thread_ids='+encodeURIComponent('['+JSON.stringify(" + JSONObject.quote(threadId) + ")+']')+'&text='+encodeURIComponent(" + JSONObject.quote(text) + ");"
            + "const r=await fetch('/api/v1/direct_v2/threads/broadcast/text/',{method:'POST',credentials:'include',headers:h,body:body});ok=r.ok;"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String fbFeed(boolean scrollMore) {
        return "(function(){const run=()=>{"
            + "const out=[],seen=new Set();"
            + "const nodes=[...document.querySelectorAll('article,[data-tracking-duration-id],[data-ft],div[role=\"article\"]')];"
            + "nodes.forEach((el,idx)=>{"
            + "const raw=(el.innerText||'').trim();if(raw.length<18)return;"
            + "const lines=raw.split('\\n').map(s=>s.trim()).filter(Boolean);"
            + "const author=lines[0]||'Facebook';"
            + "const body=lines.slice(1).filter(l=>!/^(Mi piace|Like|Commenta|Comment|Condividi|Share|\\d+)$/i.test(l)).join(' ').slice(0,320);"
            + "const key=author+':'+body.slice(0,40);if(seen.has(key))return;seen.add(key);"
            + "const pLink=el.querySelector('a[href*=\"/profile.php\"],a[href*=\"facebook.com/\"],a[href^=\"/\"]');"
            + "const cLink=el.querySelector('a[href*=\"story.php\"],a[href*=\"/posts/\"],a[href*=\"/permalink/\"],a[href*=\"comment\"]');"
            + "const imgs=[...el.querySelectorAll('img[src*=\"fbcdn\"],img[src*=\"scontent\"]')].filter(im=>im.width>110||im.naturalWidth>110);"
            + "const vid=(el.querySelector('video')||{}).src||'';"
            + "out.push({idx:idx,author:author.slice(0,45),text:body,img:imgs.length?imgs[0].src:'',vid:vid,"
            + "purl:pLink?pLink.href:'',curl:cLink?cLink.href:''});});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,items:out.slice(0,22)}));};"
            + (scrollMore ? "window.scrollBy(0,2200);setTimeout(run,1400);" : "run();") + "})();";
    }

    public static String fbLike(String idxStr) {
        return "(function(){try{const idx=parseInt(" + JSONObject.quote(idxStr) + ",10)||0;"
            + "const nodes=[...document.querySelectorAll('article,[data-tracking-duration-id],[data-ft],div[role=\"article\"]')];"
            + "const el=nodes[idx];if(el){const btn=[...el.querySelectorAll('a,button,[role=\"button\"]')].find(b=>/^(mi piace|like)$/i.test((b.innerText||'').trim()));if(btn)btn.click();}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true}));})();";
    }

    public static String fbReadPage() {
        return "(function(){try{"
            + "const h=document.querySelector('h1,h2,h3,[role=\"heading\"]');"
            + "const title=h?(h.innerText||'').trim():'Pagina Facebook';"
            + "const items=[],seen=new Set();"
            + "document.querySelectorAll('article,[data-tracking-duration-id],div[role=\"article\"],div[data-ft]').forEach(el=>{"
            + "const txt=(el.innerText||'').trim().replace(/\\s+/g,' ');"
            + "if(txt.length>15&&!seen.has(txt.slice(0,45))){seen.add(txt.slice(0,45));"
            + "const im=(el.querySelector('img[src*=\"fbcdn\"],img[src*=\"scontent\"]')||{}).src||'';"
            + "items.push({text:txt.slice(0,320),img:im});}});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,title:title,items:items.slice(0,20)}));"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,title:'',items:[]}));}})();";
    }

    public static String fbPostComment(String text) {
        return "(function(){try{"
            + "const box=document.querySelector('textarea,input[name=\"comment_text\"],[contenteditable=\"true\"]');"
            + "if(box){box.focus();if('value' in box)box.value=" + JSONObject.quote(text) + ";else box.textContent=" + JSONObject.quote(text) + ";"
            + "box.dispatchEvent(new Event('input',{bubbles:true}));}"
            + "setTimeout(()=>{const btn=[...document.querySelectorAll('button,input[type=\"submit\"],[role=\"button\"]')].find(b=>/pubblica|invia|commenta|post|send/i.test((b.innerText||b.value||'')));if(btn)btn.click();"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true}));},350);"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false}));}})();";
    }

    public static String geminiStop() {
        return "(function(){window.__gemAbort=true;try{const st=document.querySelector('button[aria-label*=\"Interrompi\"],button[aria-label*=\"Stop\"]');if(st)st.click();}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true}));})();";
    }

    public static String geminiListModels() {
        return "(function(){try{const sw=[...document.querySelectorAll('button')].find(b=>{const t=(b.innerText||'').toLowerCase();return b.getAttribute('data-test-id')==='bard-mode-menu-button'||t.includes('gemini')||t.includes('flash')||t.includes('hydrogen')||t.includes('carbon')||t.includes('pro');});"
            + "if(sw)sw.click();setTimeout(()=>{const seen=new Set(),models=[];"
            + "document.querySelectorAll('[role=\"menuitem\"],[role=\"option\"],mat-option,.gds-mode-menu-item').forEach(el=>{const line=(el.innerText||'').trim().split('\\n')[0].trim();if(line.length>1&&line.length<45&&!seen.has(line)){seen.add(line);models.push(line);}});"
            + "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,models:models}));},450);}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,models:[]}));}})();";
    }

    public static String geminiListChats() {
        return "(function(){try{const menuBtn=document.querySelector('button[aria-label*=\"Menu\"],button[aria-label*=\"menu\"]');if(menuBtn&&document.querySelectorAll('a[href*=\"/app/\"]').length<2)menuBtn.click();"
            + "setTimeout(()=>{const seen=new Set(),chats=[];document.querySelectorAll('a[href*=\"/app/\"]').forEach(a=>{const parts=(a.href||'').split('/app/');if(parts.length<2)return;const id=parts[1].split(/[?#]/)[0];if(!id||seen.has(id))return;seen.add(id);const title=(a.textContent||'').trim().replace(/\\s+/g,' ');if(title.length>1)chats.push({id:id,title:title.slice(0,60),url:a.href});});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,chats:chats.slice(0,25)}));},500);}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,chats:[]}));}})();";
    }

    public static String geminiReadThread() {
        return "(function(){try{const turns=[],seen=new Set();document.querySelectorAll('user-query,model-response,.user-query-text,.model-response-text').forEach(el=>{const tag=el.tagName.toLowerCase(),cls=(el.className||'').toString();const role=(tag.includes('user')||cls.includes('user'))?'Tu':'Gemini';const txt=(el.innerText||'').trim(),key=role+':'+txt.slice(0,40);if(txt.length>0&&!seen.has(key)){seen.add(key);turns.push({role:role,text:txt});}});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,turns:turns.slice(-20)}));}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,turns:[]}));}})();";
    }

    public static String geminiAsk(String prompt, String model, String think) {
        String prefix = "alto".equals(think) ? "[Ragionamento approfondito passo dopo passo]\n" : "basso".equals(think) ? "[Risposta breve e sintetica]\n" : "";
        String fullPrompt = prefix + prompt;
        return "(function(){window.__gemAbort=false;try{const mTarget=(" + JSONObject.quote(model) + "||'').toLowerCase();"
            + "if(mTarget&&mTarget!=='auto'){const sw=[...document.querySelectorAll('button')].find(b=>{const t=(b.innerText||'').toLowerCase();return b.getAttribute('data-test-id')==='bard-mode-menu-button'||t.includes('gemini')||t.includes('flash')||t.includes('hydrogen')||t.includes('carbon')||t.includes('pro');});if(sw)sw.click();setTimeout(()=>{const hit=[...document.querySelectorAll('[role=\"menuitem\"],[role=\"option\"],mat-option')].find(o=>(o.innerText||'').toLowerCase().includes(mTarget));if(hit)hit.click();},300);}"
            + "setTimeout(()=>{if(window.__gemAbort){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const ed=document.querySelector('div.ql-editor,[contenteditable=\"true\"],textarea');if(!ed){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Apri Gemini su Z Flip'}));return;}"
            + "ed.focus();ed.textContent='';document.execCommand('insertText',false," + JSONObject.quote(fullPrompt) + ");if(!ed.textContent)ed.textContent=" + JSONObject.quote(fullPrompt) + ";ed.dispatchEvent(new Event('input',{bubbles:true}));"
            + "const prevCnt=document.querySelectorAll('model-response,.model-response-text,message-content').length;"
            + "setTimeout(()=>{const btn=document.querySelector('button[aria-label*=\"Invia\"],button[aria-label*=\"Send\"],button.send-button');if(btn)btn.click();let last='',same=0,tries=0;"
            + "const iv=setInterval(()=>{tries++;if(window.__gemAbort){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const nodes=document.querySelectorAll('model-response,.model-response-text,message-content');const cur=nodes.length>prevCnt?nodes[nodes.length-1].innerText.trim():(nodes.length?nodes[nodes.length-1].innerText.trim():'');"
            + "if(cur&&cur===last)same++;else{last=cur;same=0;}if((same>=2&&cur.length>1)||tries>34){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,reply:cur||'Nessuna risposta.'}));}},750);},450);},500);"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:String(e)}));}})();";
    }

    public static String geminiEdit(String idxStr, String prompt, String model, String think) {
        String prefix = "alto".equals(think) ? "[Ragionamento approfondito passo dopo passo]\n" : "basso".equals(think) ? "[Risposta breve e sintetica]\n" : "";
        String fullPrompt = prefix + prompt;
        return "(function(){window.__gemAbort=false;try{const idx=parseInt(" + JSONObject.quote(idxStr) + ",10)||0;const uqs=[...document.querySelectorAll('user-query')];const uq=uqs.length>idx?uqs[uqs.length-1-idx]:null;"
            + "const editBtn=uq?uq.querySelector('button[aria-label*=\"Modifica\"],button[aria-label*=\"Edit\"]'):null;"
            + "if(!editBtn){" + geminiAsk(prompt, model, think).replaceFirst("^\\(function\\(\\)\\{", "") + ""
            + "editBtn.click();setTimeout(()=>{const box=uq.querySelector('textarea,div.ql-editor,[contenteditable=\"true\"]');if(!box){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Errore modifica'}));return;}"
            + "box.focus();if('value' in box)box.value=" + JSONObject.quote(fullPrompt) + ";else box.textContent=" + JSONObject.quote(fullPrompt) + ";box.dispatchEvent(new Event('input',{bubbles:true}));"
            + "setTimeout(()=>{const upd=uq.querySelector('button[aria-label*=\"Aggiorna\"],button[aria-label*=\"Update\"],button[aria-label*=\"Invia\"]')||[...uq.querySelectorAll('button')].pop();if(upd)upd.click();let last='',same=0,tries=0;"
            + "const iv=setInterval(()=>{tries++;if(window.__gemAbort){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const nodes=document.querySelectorAll('model-response,.model-response-text,message-content');const cur=nodes.length?nodes[nodes.length-1].innerText.trim():'';"
            + "if(cur&&cur===last)same++;else{last=cur;same=0;}if((same>=2&&cur.length>1)||tries>34){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,reply:cur||'Nessuna risposta.'}));}},750);},300);},350);"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:String(e)}));}})();";
    }
}
