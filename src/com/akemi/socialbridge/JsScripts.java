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
            + "const j=await r.json();"
            + "let reel=(j.reels_media&&j.reels_media[0])||(j.reels&&j.reels[rid]);"
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
            + "res.following=(usr.edge_follow&&usr.edge_follow.count)||0;"
            + "res.followed=!!usr.followed_by_viewer;"
            + "const r2=await fetch('/api/v1/feed/user/'+encodeURIComponent(res.id)+'/?count=18',{credentials:'include',headers:h});"
            + "if(r2.ok){const j2=await r2.json();(j2.items||[]).forEach(m=>{const it=mapM(m);if(it)res.items.push(it);});}"
            + "if(!res.items.length){((usr.edge_owner_to_timeline_media&&usr.edge_owner_to_timeline_media.edges)||[]).forEach(e=>{"
            + "const n=e.node||{};const cap=((n.edge_media_to_caption&&n.edge_media_to_caption.edges&&n.edge_media_to_caption.edges[0])||{node:{}}).node.text||'';"
            + "res.items.push({id:String(n.id||''),user:res.user,text:cap,likes:(n.edge_liked_by&&n.edge_liked_by.count)||0,comments:(n.edge_media_to_comment&&n.edge_media_to_comment.count)||0,img:n.display_url||'',imgs:[n.display_url||''],vid:n.video_url||''});});}}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igFollow(String userId, boolean unfollow) {
        String act = unfollow ? "unfollow" : "follow";
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const r=await fetch('/api/v1/web/friendships/'+encodeURIComponent(" + JSONObject.quote(userId) + ")+'/" + act + "/',{method:'POST',credentials:'include',headers:h});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String igSearch(String q) {
        return "(async function(){let res={ok:true,users:[],tags:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/web/search/topsearch/?context=blended&query='+encodeURIComponent(" + JSONObject.quote(q) + "),{credentials:'include',headers:h});"
            + "const j=await r.json();"
            + "(j.users||[]).slice(0,15).forEach(w=>{const u=w.user||{};res.users.push({user:u.username||'',name:u.full_name||'',avatar:u.profile_pic_url||''});});"
            + "(j.hashtags||[]).slice(0,8).forEach(w=>{const t=w.hashtag||{};res.tags.push({name:t.name||'',count:t.media_count||0});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
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
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igAddComment(String mediaId, String text) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const body='comment_text='+encodeURIComponent(" + JSONObject.quote(text) + ");"
            + "const r=await fetch('/api/v1/web/comments/'+encodeURIComponent(" + JSONObject.quote(mediaId) + ")+'/add/',{method:'POST',credentials:'include',headers:h,body:body});"
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
            + "const j=await r.json();res.threads=((j.inbox&&j.inbox.threads)||[]).map(t=>({"
            + "id:t.thread_id||'',title:t.thread_title||((t.users&&t.users[0]&&t.users[0].username)||'Chat'),"
            + "last:(t.last_permanent_item&&t.last_permanent_item.text)||'[Media]'}));"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igDmThread(String threadId) {
        return "(async function(){let res={ok:true,messages:[]};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/direct_v2/threads/'+encodeURIComponent(" + JSONObject.quote(threadId) + ")+'/?limit=20',{credentials:'include',headers:h});"
            + "const j=await r.json();const th=j.thread||{};const users={};"
            + "(th.users||[]).forEach(u=>{users[String(u.pk)]=u.username;});"
            + "(th.items||[]).reverse().forEach(it=>{const u=users[String(it.user_id)];"
            + "res.messages.push({sender:u||'Tu',isMe:!u,text:it.text||(it.item_type==='media'?'[Foto/Video]':'[Messaggio]')});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igDmSend(String threadId, String text) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const body='action=send_item&thread_ids='+encodeURIComponent('['+JSON.stringify(" + JSONObject.quote(threadId) + ")+']')+'&text='+encodeURIComponent(" + JSONObject.quote(text) + ");"
            + "const r=await fetch('/api/v1/direct_v2/threads/broadcast/text/',{method:'POST',credentials:'include',headers:h,body:body});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String redditFeed(String sub, String sort, String after) {
        String s = sub == null ? "" : sub.trim();
        String base = s.isEmpty() ? "/.json?limit=20&raw_json=1"
                : ("day".equals(sort) || "week".equals(sort) || "month".equals(sort))
                ? "/r/" + s + "/top.json?t=" + sort + "&limit=20&raw_json=1"
                : "new".equals(sort) ? "/r/" + s + "/new.json?limit=20&raw_json=1"
                : "/r/" + s + "/hot.json?limit=20&raw_json=1";
        String path = (after != null && !after.isEmpty()) ? base + "&after=" + after : base;
        return "(async function(){try{"
            + "const r=await fetch(" + JSONObject.quote(path) + ",{credentials:'include'});"
            + "const j=await r.json();if(j&&j.data){AndroidBridge.done('__REQ_ID__',JSON.stringify(j));return;}"
            + "}catch(e){}"
            + "const sp=[...document.querySelectorAll('shreddit-post')];"
            + "const ch=sp.slice(0,20).map(p=>{const im=p.querySelector('img[src*=\"redd.it\"],img[src*=\"redditmedia\"]');"
            + "return {data:{name:p.getAttribute('id')||'',title:p.getAttribute('post-title')||'',author:p.getAttribute('author')||'',"
            + "subreddit:(p.getAttribute('subreddit-prefixed-name')||'').replace('r/',''),"
            + "score:parseInt(p.getAttribute('score')||'0',10)||0,num_comments:parseInt(p.getAttribute('comment-count')||'0',10)||0,"
            + "permalink:p.getAttribute('permalink')||'',url:im?im.src:(p.getAttribute('content-href')||'')}};});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({data:{children:ch,after:''}}));})();";
    }

    public static String redditComments(String permalink) {
        String clean = permalink.replaceAll("^https?://[^/]+", "").split("\\?")[0];
        if (!clean.startsWith("/")) clean = "/" + clean;
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        String url = clean + ".json?limit=35&depth=4&raw_json=1";
        return "(async function(){try{"
            + "const r=await fetch(" + JSONObject.quote(url) + ",{credentials:'include'});"
            + "const j=await r.json();if(Array.isArray(j)){AndroidBridge.done('__REQ_ID__',JSON.stringify(j));return;}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__','[]');})();";
    }

    public static String redditUser(String username) {
        return "(async function(){let res={ok:false,items:[]};try{"
            + "const u=" + JSONObject.quote(username) + ";"
            + "const r1=await fetch('/user/'+encodeURIComponent(u)+'/about.json',{credentials:'include'});"
            + "const j1=await r1.json();const d=j1.data||{};"
            + "res.ok=true;res.user=d.name||u;res.karma=(d.total_karma||(d.link_karma||0)+(d.comment_karma||0));"
            + "res.bio=(d.subreddit&&d.subreddit.public_description)||'';"
            + "const r2=await fetch('/user/'+encodeURIComponent(u)+'/submitted.json?limit=20&raw_json=1',{credentials:'include'});"
            + "const j2=await r2.json();res.items=(j2.data&&j2.data.children)||[];"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String redditSubInfo(String sub) {
        return "(async function(){let res={ok:false};try{"
            + "const r=await fetch('/r/'+encodeURIComponent(" + JSONObject.quote(sub) + ")+'/about.json',{credentials:'include'});"
            + "const j=await r.json();const d=j.data||{};"
            + "res={ok:true,sub:d.display_name||" + JSONObject.quote(sub) + ",title:d.title||'',desc:d.public_description||'',subs:d.subscribers||0,subscribed:!!d.user_is_subscriber};"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String redditSearch(String q) {
        return "(async function(){let res={ok:true,subs:[],posts:[]};try{"
            + "const qu=encodeURIComponent(" + JSONObject.quote(q) + ");"
            + "const r1=await fetch('/subreddits/search.json?q='+qu+'&limit=8',{credentials:'include'});"
            + "const j1=await r1.json();res.subs=((j1.data&&j1.data.children)||[]).map(c=>({sub:c.data.display_name,subs:c.data.subscribers||0}));"
            + "const r2=await fetch('/search.json?q='+qu+'&limit=18&raw_json=1',{credentials:'include'});"
            + "const j2=await r2.json();res.posts=(j2.data&&j2.data.children)||[];"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String redditSaved() {
        return "(async function(){try{"
            + "const me=await (await fetch('/api/me.json',{credentials:'include'})).json();"
            + "const u=me.data&&me.data.name;if(!u){AndroidBridge.done('__REQ_ID__','{\"data\":{\"children\":[]}}');return;}"
            + "const r=await fetch('/user/'+encodeURIComponent(u)+'/saved.json?limit=25&raw_json=1',{credentials:'include'});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify(await r.json()));"
            + "}catch(e){AndroidBridge.done('__REQ_ID__','{\"data\":{\"children\":[]}}');}})();";
    }

    public static String redditAction(String endpoint, String bodyParams) {
        return "(async function(){let ok=false;try{"
            + "const me=await (await fetch('/api/me.json',{credentials:'include'})).json();"
            + "const uh=(me.data&&me.data.modhash)||'';"
            + "const r=await fetch(" + JSONObject.quote(endpoint) + ",{method:'POST',credentials:'include',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Modhash':uh},body:" + JSONObject.quote(bodyParams) + "+'&uh='+encodeURIComponent(uh)});"
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

    public static String geminiStop() {
        return "(function(){window.__gemAbort=true;try{"
            + "const st=document.querySelector('button[aria-label*=\"Interrompi\"],button[aria-label*=\"Stop\"],button[data-test-id=\"stop-button\"]');"
            + "if(st)st.click();"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true}));})();";
    }

    public static String geminiListModels() {
        return "(function(){try{"
            + "const btns=[...document.querySelectorAll('button')];"
            + "const sw=btns.find(b=>{const t=(b.innerText||'').toLowerCase();return b.getAttribute('data-test-id')==='bard-mode-menu-button'||t.includes('gemini')||t.includes('flash')||t.includes('hydrogen')||t.includes('carbon')||t.includes('pro');});"
            + "if(sw)sw.click();"
            + "setTimeout(()=>{"
            + "const seen=new Set(),models=[];"
            + "document.querySelectorAll('[role=\"menuitem\"],[role=\"option\"],mat-option,.gds-mode-menu-item').forEach(el=>{"
            + "const line=(el.innerText||'').trim().split('\\n')[0].trim();"
            + "if(line.length>1&&line.length<45&&!seen.has(line)){seen.add(line);models.push(line);}});"
            + "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,models:models}));"
            + "},450);}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,models:[]}));}})();";
    }

    public static String geminiListChats() {
        return "(function(){try{"
            + "const menuBtn=document.querySelector('button[aria-label*=\"Menu\"],button[aria-label*=\"menu\"],button[data-test-id=\"side-nav-menu-button\"]');"
            + "if(menuBtn&&document.querySelectorAll('a[href*=\"/app/\"]').length<2)menuBtn.click();"
            + "setTimeout(()=>{"
            + "const seen=new Set(),chats=[];"
            + "document.querySelectorAll('a[href*=\"/app/\"]').forEach(a=>{"
            + "const parts=(a.href||'').split('/app/');if(parts.length<2)return;"
            + "const id=parts[1].split(/[?#]/)[0];if(!id||seen.has(id))return;seen.add(id);"
            + "const title=(a.textContent||'').trim().replace(/\\s+/g,' ');"
            + "if(title.length>1)chats.push({id:id,title:title.slice(0,60),url:a.href});});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,chats:chats.slice(0,25)}));"
            + "},500);}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,chats:[]}));}})();";
    }

    public static String geminiReadThread() {
        return "(function(){try{"
            + "const turns=[],seen=new Set();"
            + "document.querySelectorAll('user-query,model-response,.user-query-text,.model-response-text').forEach(el=>{"
            + "const tag=el.tagName.toLowerCase();const cls=(el.className||'').toString();"
            + "const role=(tag.includes('user')||cls.includes('user'))?'Tu':'Gemini';"
            + "const txt=(el.innerText||'').trim();const key=role+':'+txt.slice(0,40);"
            + "if(txt.length>0&&!seen.has(key)){seen.add(key);turns.push({role:role,text:txt});}});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,turns:turns.slice(-20)}));"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,turns:[]}));}})();";
    }

    public static String geminiAsk(String prompt, String model, String think) {
        String prefix = "alto".equals(think)
                ? "[Usa ragionamento approfondito passo dopo passo prima di rispondere]\n"
                : "basso".equals(think)
                ? "[Risposta rapida e sintetica in poche righe]\n" : "";
        String fullPrompt = prefix + prompt;
        return "(function(){window.__gemAbort=false;try{"
            + "const mTarget=(" + JSONObject.quote(model) + "||'').toLowerCase();"
            + "if(mTarget&&mTarget!=='auto'){"
            + "const sw=[...document.querySelectorAll('button')].find(b=>{const t=(b.innerText||'').toLowerCase();return b.getAttribute('data-test-id')==='bard-mode-menu-button'||t.includes('gemini')||t.includes('flash')||t.includes('hydrogen')||t.includes('carbon')||t.includes('pro');});"
            + "if(sw)sw.click();"
            + "setTimeout(()=>{"
            + "const opts=[...document.querySelectorAll('[role=\"menuitem\"],[role=\"option\"],mat-option,.gds-mode-menu-item')];"
            + "const hit=opts.find(o=>(o.innerText||'').toLowerCase().includes(mTarget));"
            + "if(hit)hit.click();},300);}"
            + "setTimeout(()=>{"
            + "if(window.__gemAbort){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const ed=document.querySelector('div.ql-editor,[contenteditable=\"true\"],textarea');"
            + "if(!ed){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Apri scheda Gemini su Z Flip'}));return;}"
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
            + "if(window.__gemAbort){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const nodes=document.querySelectorAll('model-response,.model-response-text,message-content');"
            + "const cur=nodes.length>prevCnt?nodes[nodes.length-1].innerText.trim():(nodes.length?nodes[nodes.length-1].innerText.trim():'');"
            + "if(cur&&cur===last)same++;else{last=cur;same=0;}"
            + "if((same>=2&&cur.length>1)||tries>34){clearInterval(iv);"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,reply:cur||'Nessuna risposta.'}));}"
            + "},750);},450);},500);"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:String(e)}));}})();";
    }

    public static String geminiEdit(String idxStr, String prompt, String model, String think) {
        String prefix = "alto".equals(think)
                ? "[Usa ragionamento approfondito passo dopo passo prima di rispondere]\n"
                : "basso".equals(think)
                ? "[Risposta rapida e sintetica in poche righe]\n" : "";
        String fullPrompt = prefix + prompt;
        return "(function(){window.__gemAbort=false;try{"
            + "const idx=parseInt(" + JSONObject.quote(idxStr) + ",10)||0;"
            + "const uqs=[...document.querySelectorAll('user-query')];"
            + "const uq=uqs.length>idx?uqs[uqs.length-1-idx]:null;"
            + "if(uq){uq.dispatchEvent(new MouseEvent('mouseover',{bubbles:true}));}"
            + "const editBtn=uq?uq.querySelector('button[aria-label*=\"Modifica\"],button[aria-label*=\"Edit\"]'):null;"
            + "if(!editBtn){" + geminiAsk(prompt, model, think).replaceFirst("^\\(function\\(\\)\\{", "") + ""
            + "editBtn.click();"
            + "setTimeout(()=>{"
            + "const box=uq.querySelector('textarea,div.ql-editor,[contenteditable=\"true\"]');"
            + "if(!box){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Campo modifica non trovato'}));return;}"
            + "box.focus();if('value' in box)box.value=" + JSONObject.quote(fullPrompt) + ";else box.textContent=" + JSONObject.quote(fullPrompt) + ";"
            + "box.dispatchEvent(new Event('input',{bubbles:true}));box.dispatchEvent(new Event('change',{bubbles:true}));"
            + "setTimeout(()=>{"
            + "const upd=uq.querySelector('button[aria-label*=\"Aggiorna\"],button[aria-label*=\"Update\"],button[aria-label*=\"Invia\"],button[aria-label*=\"Send\"]')||[...uq.querySelectorAll('button')].pop();"
            + "if(upd)upd.click();"
            + "let last='',same=0,tries=0;"
            + "const iv=setInterval(()=>{tries++;"
            + "if(window.__gemAbort){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const nodes=document.querySelectorAll('model-response,.model-response-text,message-content');"
            + "const cur=nodes.length?nodes[nodes.length-1].innerText.trim():'';"
            + "if(cur&&cur===last)same++;else{last=cur;same=0;}"
            + "if((same>=2&&cur.length>1)||tries>34){clearInterval(iv);"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,reply:cur||'Nessuna risposta.'}));}"
            + "},750);},300);},350);"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:String(e)}));}})();";
    }
}
