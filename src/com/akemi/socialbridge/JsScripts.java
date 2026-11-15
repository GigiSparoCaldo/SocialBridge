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
        return "const bestImg=cands=>{if(!cands||!cands.length)return '';let b=cands[0];cands.forEach(c=>{if(Math.abs((c.width||0)-720)<Math.abs((b.width||0)-720))b=c;});return b.url||'';};"
            + "const bestVid=vs=>{if(!vs||!vs.length)return '';const s=[...vs].sort((a,b)=>(a.width||9999)-(b.width||9999));return (s[0]&&s[0].url)||'';};"
            + "const mapM=m=>{if(!m||!m.user)return null;let imgs=[],vid='';"
            + "if(m.carousel_media&&m.carousel_media.length){m.carousel_media.forEach(cm=>{"
            + "const u=bestImg(cm.image_versions2&&cm.image_versions2.candidates);if(u)imgs.push(u);"
            + "if(!vid)vid=bestVid(cm.video_versions);});}"
            + "else{const u=bestImg(m.image_versions2&&m.image_versions2.candidates);if(u)imgs.push(u);else if(m.display_url)imgs.push(m.display_url);}"
            + "if(!vid)vid=bestVid(m.video_versions);"
            + "if(!imgs.length&&!vid)return null;"
            + "return {id:String(m.pk||m.id||''),user:m.user.username||'',text:(m.caption&&m.caption.text)||'',likes:m.like_count||0,comments:m.comment_count||0,img:imgs[0]||'',imgs:imgs,vid:vid};};"
            + "const walkM=(obj,out,seen)=>{if(!obj||typeof obj!=='object'||out.length>=24)return;"
            + "if((obj.pk||obj.id)&&obj.user&&(obj.image_versions2||obj.carousel_media||obj.video_versions||obj.display_url)){"
            + "const it=mapM(obj);if(it&&!seen.has(it.id)){seen.add(it.id);out.push(it);}return;}"
            + "Object.values(obj).forEach(v=>{if(Array.isArray(v))v.forEach(x=>walkM(x,out,seen));else if(v&&typeof v==='object')walkM(v,out,seen);});};";
    }

    public static String igFeed(String maxId) {
        String body = (maxId == null || maxId.isEmpty())
                ? "'reason=cold_start_fetch&is_pull_to_refresh=0'"
                : "'reason=pagination&max_id='+encodeURIComponent(" + JSONObject.quote(maxId) + ")";
        return "(async function(){let res={ok:true,items:[],next:''};try{" + igHdrs(true) + mapIgMediaFn()
            + "const r=await fetch('/api/v1/feed/timeline/',{method:'POST',credentials:'include',headers:h,body:" + body + "});"
            + "if(r.ok){const j=await r.json();res.next=String(j.next_max_id||'');walkM(j.feed_items||j,res.items,new Set());}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igExplore() {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(false) + mapIgMediaFn()
            + "const r=await fetch('/api/v1/discover/web/explore_grid/?include_fixed_destinations=true&is_nonpersonalized_explore=false',{credentials:'include',headers:h});"
            + "if(r.ok){const j=await r.json();walkM(j,res.items,new Set());}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igTagFeed(String tag) {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(false) + mapIgMediaFn()
            + "const r=await fetch('/api/v1/tags/web_info/?tag_name='+encodeURIComponent(" + JSONObject.quote(tag) + "),{credentials:'include',headers:h});"
            + "if(r.ok){const j=await r.json();walkM(j,res.items,new Set());}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igSaved() {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(false) + mapIgMediaFn()
            + "const r=await fetch('/api/v1/feed/saved/posts/',{credentials:'include',headers:h});"
            + "if(r.ok){const j=await r.json();walkM(j,res.items,new Set());}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igStories() {
        return "(async function(){let res={ok:true,stories:[]};window.__igTray=window.__igTray||{};try{" + igHdrs(false)
            + "const r=await fetch('/api/v1/feed/reels_tray/',{credentials:'include',headers:h});"
            + "const j=await r.json();(j.tray||[]).slice(0,22).forEach(t=>{"
            + "const rid=String(t.id||'');const uid=String((t.user&&(t.user.pk_id||t.user.pk))||rid);const u=(t.user&&t.user.username)||'';"
            + "if(t.items&&t.items.length){if(rid)window.__igTray[rid]=t.items;if(uid)window.__igTray[uid]=t.items;}"
            + "if((rid||uid)&&u)res.stories.push({id:rid||uid,uid:uid,user:u,avatar:(t.user&&t.user.profile_pic_url)||''});});"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igStoryMedia(String reelId, String userId) {
        return "(async function(){let res={ok:true,items:[]};try{" + igHdrs(false)
            + "const rid=String(" + JSONObject.quote(reelId) + ").replace(/^user:/,'');"
            + "const uid=String(" + JSONObject.quote(userId) + ").replace(/^user:/,'')||rid;"
            + "const pickI=c=>c&&c.length?c[0].url:'';"
            + "const pickV=v=>{if(!v||!v.length)return '';const s=[...v].sort((a,b)=>(a.width||9999)-(b.width||9999));return s[0].url||'';};"
            + "const pushRaw=arr=>(arr||[]).forEach(m=>{const im=pickI(m.image_versions2&&m.image_versions2.candidates)||m.display_url||'';const vd=pickV(m.video_versions);if(im||vd)res.items.push({img:im,vid:vd});});"
            + "pushRaw((window.__igTray&&(window.__igTray[rid]||window.__igTray[uid]))||[]);"
            + "if(!res.items.length){const q=rid===uid?('reel_ids='+encodeURIComponent(rid)):('reel_ids='+encodeURIComponent(rid)+'&reel_ids='+encodeURIComponent(uid));"
            + "try{const r1=await fetch('/api/v1/feed/reels_media/?'+q,{credentials:'include',headers:h});if(r1.ok){const j1=await r1.json();"
            + "(j1.reels_media||[]).forEach(rl=>pushRaw(rl.items));"
            + "if(!res.items.length&&j1.reels)Object.values(j1.reels).forEach(rl=>pushRaw(rl.items));}}catch(e){}}"
            + "if(!res.items.length){try{const r2=await fetch('/api/v1/feed/user/'+encodeURIComponent(uid)+'/story/',{credentials:'include',headers:h});"
            + "if(r2.ok){const j2=await r2.json();if(j2.reel)pushRaw(j2.reel.items);}}catch(e){}}"
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
            + "try{const r2=await fetch('/api/v1/feed/user/'+encodeURIComponent(res.id)+'/?count=18',{credentials:'include',headers:h});"
            + "if(r2.ok){const j2=await r2.json();walkM(j2,res.items,new Set());}}catch(e){}"
            + "if(!res.items.length){((usr.edge_owner_to_timeline_media&&usr.edge_owner_to_timeline_media.edges)||[]).forEach(e=>{"
            + "const n=e.node||{};const cap=((n.edge_media_to_caption&&n.edge_media_to_caption.edges&&n.edge_media_to_caption.edges[0])||{node:{}}).node.text||'';"
            + "res.items.push({id:String(n.id||''),user:res.user,text:cap,likes:(n.edge_liked_by&&n.edge_liked_by.count)||0,comments:(n.edge_media_to_comment&&n.edge_media_to_comment.count)||0,img:n.display_url||'',imgs:[n.display_url||''],vid:n.video_url||''});});}}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String igFollow(String userId, boolean unfollow) {
        return "(async function(){let ok=false;try{" + igHdrs(true)
            + "const r=await fetch('/api/v1/web/friendships/'+encodeURIComponent(" + JSONObject.quote(userId) + ")+'/" + (unfollow ? "unfollow" : "follow") + "/',{method:'POST',credentials:'include',headers:h});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
    }

    public static String igSearch(String q) {
        return "(async function(){let res={ok:true,users:[],tags:[]};const seenU=new Set(),seenT=new Set();try{" + igHdrs(false)
            + "const qu=encodeURIComponent(" + JSONObject.quote(q) + ");"
            + "const addU=u=>{if(u&&u.username&&!seenU.has(u.username)){seenU.add(u.username);res.users.push({user:u.username,name:u.full_name||''});}};"
            + "const addT=t=>{if(t&&t.name&&!seenT.has(t.name)){seenT.add(t.name);res.tags.push({name:t.name,count:t.media_count||0});}};"
            + "const urls=['/api/v1/fbsearch/web/top_serp/?enable_metadata=true&query='+qu,'/api/v1/web/search/topsearch/?context=blended&query='+qu,'/api/v1/users/search/?q='+qu];"
            + "for(const u of urls){if(res.users.length>=10)break;try{const r=await fetch(u,{credentials:'include',headers:h});if(r.ok){const j=await r.json();"
            + "(j.users||[]).forEach(w=>addU(w.user||w));(j.hashtags||[]).forEach(w=>addT(w.hashtag||w));"
            + "(j.list||[]).forEach(w=>{if(w.user)addU(w.user);if(w.hashtag)addT(w.hashtag);});}}catch(e){}}"
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

    public static String redditFeed(String sub, String sort, String after) {
        String s = sub == null ? "" : sub.trim();
        String base = s.isEmpty() ? "/.json?limit=20&raw_json=1"
                : ("day".equals(sort) || "week".equals(sort) || "month".equals(sort))
                ? "/r/" + s + "/top.json?t=" + sort + "&limit=20&raw_json=1"
                : "new".equals(sort) ? "/r/" + s + "/new.json?limit=20&raw_json=1"
                : "/r/" + s + "/hot.json?limit=20&raw_json=1";
        String path = (after != null && !after.isEmpty()) ? base + "&after=" + after : base;
        return "(async function(){try{"
            + "const r=await fetch(" + JSONObject.quote(path) + ",{credentials:'include',headers:{'Accept':'application/json'}});"
            + "const j=await r.json();if(j&&j.data){AndroidBridge.done('__REQ_ID__',JSON.stringify(j));return;}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__','{\"data\":{\"children\":[]}}');})();";
    }

    public static String redditComments(String permalink) {
        String clean = permalink.replaceAll("^https?://[^/]+", "").split("\\?")[0];
        if (!clean.startsWith("/")) clean = "/" + clean;
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        String url = clean + ".json?limit=35&depth=4&raw_json=1";
        return "(async function(){try{"
            + "const r=await fetch(" + JSONObject.quote(url) + ",{credentials:'include',headers:{'Accept':'application/json'}});"
            + "const j=await r.json();if(Array.isArray(j)){AndroidBridge.done('__REQ_ID__',JSON.stringify(j));return;}"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__','[]');})();";
    }

    public static String redditUser(String username) {
        return "(async function(){let res={ok:false,items:[]};try{"
            + "const u=encodeURIComponent(" + JSONObject.quote(username) + ");"
            + "const j1=await (await fetch('/user/'+u+'/about.json',{credentials:'include'})).json();const d=j1.data||{};"
            + "res.ok=true;res.user=d.name||" + JSONObject.quote(username) + ";res.karma=(d.total_karma||(d.link_karma||0)+(d.comment_karma||0));"
            + "res.bio=(d.subreddit&&d.subreddit.public_description)||'';"
            + "const j2=await (await fetch('/user/'+u+'/submitted.json?limit=20&raw_json=1',{credentials:'include'})).json();"
            + "res.items=(j2.data&&j2.data.children)||[];"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String redditSubInfo(String sub) {
        return "(async function(){let res={ok:false};try{"
            + "const j=await (await fetch('/r/'+encodeURIComponent(" + JSONObject.quote(sub) + ")+'/about.json',{credentials:'include'})).json();"
            + "const d=j.data||{};res={ok:true,sub:d.display_name||" + JSONObject.quote(sub) + ",desc:d.public_description||'',subs:d.subscribers||0,subscribed:!!d.user_is_subscriber};"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String redditSearch(String q) {
        return "(async function(){let res={ok:true,subs:[],posts:[]};try{"
            + "const qu=encodeURIComponent(" + JSONObject.quote(q) + ");"
            + "const j1=await (await fetch('/subreddits/search.json?q='+qu+'&limit=8',{credentials:'include'})).json();"
            + "res.subs=((j1.data&&j1.data.children)||[]).map(c=>({sub:c.data.display_name,subs:c.data.subscribers||0}));"
            + "const j2=await (await fetch('/search.json?q='+qu+'&limit=18&raw_json=1',{credentials:'include'})).json();"
            + "res.posts=(j2.data&&j2.data.children)||[];"
            + "}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify(res));})();";
    }

    public static String redditSaved() {
        return "(async function(){try{"
            + "const me=await (await fetch('/api/me.json',{credentials:'include'})).json();"
            + "const u=me.data&&me.data.name;if(!u){AndroidBridge.done('__REQ_ID__','{\"data\":{\"children\":[]}}');return;}"
            + "const j=await (await fetch('/user/'+encodeURIComponent(u)+'/saved.json?limit=25&raw_json=1',{credentials:'include'})).json();"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify(j));"
            + "}catch(e){AndroidBridge.done('__REQ_ID__','{\"data\":{\"children\":[]}}');}})();";
    }

    public static String redditAction(String endpoint, String bodyParams) {
        return "(async function(){let ok=false;try{"
            + "const me=await (await fetch('/api/me.json',{credentials:'include'})).json();"
            + "const uh=(me.data&&me.data.modhash)||'';"
            + "const r=await fetch(" + JSONObject.quote(endpoint) + ",{method:'POST',credentials:'include',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Modhash':uh},body:" + JSONObject.quote(bodyParams) + "+'&uh='+encodeURIComponent(uh)});"
            + "ok=r.ok;}catch(e){}AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:ok}));})();";
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
            + "out.push({idx:idx,author:author.slice(0,45),text:body,img:imgs.length?imgs[0].src:'',vid:vid,purl:pLink?pLink.href:'',curl:cLink?cLink.href:''});});"
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
        return "(function(){try{const items=[],seen=new Set();"
            + "document.querySelectorAll('article,[data-tracking-duration-id],div[role=\"article\"],div[data-ft]').forEach(el=>{"
            + "const txt=(el.innerText||'').trim().replace(/\\s+/g,' ');"
            + "if(txt.length>15&&!seen.has(txt.slice(0,45))){seen.add(txt.slice(0,45));"
            + "const im=(el.querySelector('img[src*=\"fbcdn\"],img[src*=\"scontent\"]')||{}).src||'';"
            + "items.push({text:txt.slice(0,320),img:im});}});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,items:items.slice(0,20)}));"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,items:[]}));}})();";
    }

    public static String fbPostComment(String text) {
        return "(function(){try{const box=document.querySelector('textarea,input[name=\"comment_text\"],[contenteditable=\"true\"]');"
            + "if(box){box.focus();if('value' in box)box.value=" + JSONObject.quote(text) + ";else box.textContent=" + JSONObject.quote(text) + ";box.dispatchEvent(new Event('input',{bubbles:true}));}"
            + "setTimeout(()=>{const btn=[...document.querySelectorAll('button,input[type=\"submit\"],[role=\"button\"]')].find(b=>/pubblica|invia|commenta|post|send/i.test((b.innerText||b.value||'')));if(btn)btn.click();"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true}));},350);}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false}));}})();";
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
        return "(function(){try{const turns=[],seen=new Set();document.querySelectorAll('user-query,model-response').forEach(el=>{const role=el.tagName.toLowerCase().includes('user')?'Tu':'Gemini';const txt=(el.innerText||'').trim(),key=role+':'+txt.slice(0,40);if(txt.length>0&&!seen.has(key)){seen.add(key);turns.push({role:role,text:txt});}});"
            + "AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,turns:turns.slice(-20)}));}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,turns:[]}));}})();";
    }

    public static String geminiAsk(String prompt, String model, String think) {
        String prefix = "alto".equals(think) ? "[Ragionamento approfondito passo dopo passo]\n" : "basso".equals(think) ? "[Risposta breve e sintetica]\n" : "";
        String fullPrompt = prefix + prompt;
        return "(function(){window.__gemAbort=false;try{"
            + "const getR=()=>{let l=[...document.querySelectorAll('model-response')];if(!l.length)l=[...document.querySelectorAll('.model-response-text,message-content')];return l;};"
            + "const initR=getR();const prevCnt=initR.length;const prevLastTxt=prevCnt?(initR[prevCnt-1].innerText||'').trim():'';"
            + "const mTarget=(" + JSONObject.quote(model) + "||'').toLowerCase();"
            + "if(mTarget&&mTarget!=='auto'){const sw=[...document.querySelectorAll('button')].find(b=>{const t=(b.innerText||'').toLowerCase();return b.getAttribute('data-test-id')==='bard-mode-menu-button'||t.includes('gemini')||t.includes('flash')||t.includes('hydrogen')||t.includes('carbon')||t.includes('pro');});if(sw)sw.click();setTimeout(()=>{const hit=[...document.querySelectorAll('[role=\"menuitem\"],[role=\"option\"],mat-option')].find(o=>(o.innerText||'').toLowerCase().includes(mTarget));if(hit)hit.click();},250);}"
            + "setTimeout(()=>{if(window.__gemAbort){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const ed=document.querySelector('div.ql-editor,rich-textarea [contenteditable=\"true\"],[contenteditable=\"true\"],textarea');"
            + "if(!ed){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Apri Gemini su Z Flip'}));return;}"
            + "ed.focus();const fp=" + JSONObject.quote(fullPrompt) + ";"
            + "if('value' in ed)ed.value=fp;else ed.innerHTML='<p>'+fp.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/\\n/g,'<br>')+'</p>';"
            + "ed.dispatchEvent(new Event('input',{bubbles:true}));"
            + "setTimeout(()=>{const btn=document.querySelector('button[aria-label*=\"Invia\"],button[aria-label*=\"Send\"],button.send-button');if(btn)btn.click();"
            + "let last='',same=0,tries=0;"
            + "const iv=setInterval(()=>{tries++;if(window.__gemAbort){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const nodes=getR();let cur='';"
            + "if(nodes.length>prevCnt)cur=(nodes[nodes.length-1].innerText||'').trim();"
            + "else if(nodes.length>0){const cand=(nodes[nodes.length-1].innerText||'').trim();if(cand!==prevLastTxt)cur=cand;}"
            + "const busy=!!document.querySelector('button[aria-label*=\"Interrompi\"],button[aria-label*=\"Stop\"],button[data-test-id=\"stop-button\"]');"
            + "if(cur.length>1&&!busy&&cur===last)same++;else{last=cur;same=0;}"
            + "if((same>=2&&cur.length>1)||tries>42){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,reply:cur||'Nessuna risposta.'}));}},700);},400);},450);"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:String(e)}));}})();";
    }

    public static String geminiEdit(String idxStr, String prompt, String model, String think) {
        String prefix = "alto".equals(think) ? "[Ragionamento approfondito passo dopo passo]\n" : "basso".equals(think) ? "[Risposta breve e sintetica]\n" : "";
        String fullPrompt = prefix + prompt;
        return "(function(){window.__gemAbort=false;try{"
            + "const getR=()=>{let l=[...document.querySelectorAll('model-response')];if(!l.length)l=[...document.querySelectorAll('.model-response-text,message-content')];return l;};"
            + "const initR=getR();const prevLastTxt=initR.length?(initR[initR.length-1].innerText||'').trim():'';"
            + "const idx=parseInt(" + JSONObject.quote(idxStr) + ",10)||0;const uqs=[...document.querySelectorAll('user-query')];const uq=uqs.length>idx?uqs[uqs.length-1-idx]:null;"
            + "const editBtn=uq?uq.querySelector('button[aria-label*=\"Modifica\"],button[aria-label*=\"Edit\"]'):null;"
            + "if(!editBtn){" + geminiAsk(prompt, model, think).replaceFirst("^\\(function\\(\\)\\{", "") + ""
            + "editBtn.click();setTimeout(()=>{const box=uq.querySelector('textarea,div.ql-editor,[contenteditable=\"true\"]');if(!box){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Errore modifica'}));return;}"
            + "box.focus();if('value' in box)box.value=" + JSONObject.quote(fullPrompt) + ";else box.textContent=" + JSONObject.quote(fullPrompt) + ";box.dispatchEvent(new Event('input',{bubbles:true}));"
            + "setTimeout(()=>{const upd=uq.querySelector('button[aria-label*=\"Aggiorna\"],button[aria-label*=\"Update\"],button[aria-label*=\"Invia\"]')||[...uq.querySelectorAll('button')].pop();if(upd)upd.click();let last='',same=0,tries=0;"
            + "const iv=setInterval(()=>{tries++;if(window.__gemAbort){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:'Interrotto.'}));return;}"
            + "const nodes=getR();const cand=nodes.length?(nodes[nodes.length-1].innerText||'').trim():'';const cur=cand!==prevLastTxt?cand:'';"
            + "const busy=!!document.querySelector('button[aria-label*=\"Interrompi\"],button[aria-label*=\"Stop\"]');"
            + "if(cur.length>1&&!busy&&cur===last)same++;else{last=cur;same=0;}"
            + "if((same>=2&&cur.length>1)||tries>42){clearInterval(iv);AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:true,reply:cur||cand||'Nessuna risposta.'}));}},700);},350);},350);"
            + "}catch(e){AndroidBridge.done('__REQ_ID__',JSON.stringify({ok:false,reply:String(e)}));}})();";
    }
}
