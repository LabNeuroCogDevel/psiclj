async function get_anchor(task) {
  const resp = await fetch("/anchor/"+task);
  const ret = await resp.json()
  return ret.anchor;
}
async function url_params(){
    var url = new URL(document.URL);

    // id and task from mturk. 
    // TODO: should hitId have a lookup in psiclj?
    params = {"id": url.searchParams.get("workerId"),
              "timepoint": url.searchParams.get("assignmentId"),
              "task": url.searchParams.get("hitId"),
              "hash": url.hash,
              "anchor": "vanilla", //populate from /anchor/$hitId
              "external": "https://www.mturk.com/mturk/externalSubmit" };

    
    //if preview. dont show the "PLAY" link
    if(!params["id"] ||
       params["id"] === "ASSIGNMENT_ID_NOT_AVAILABLE"){
        return(undefined);
    }

    // redirect to sandbox if that's anywhere in the url
    // NB!! if some task parameter uses the word sandbox, this will cause some pain
    if( document.URL.indexOf("sandbox") >= 0 ||
        (document.parent?(document.parent.URL.indexOf("sandbox")>=0):false)) {
     params["external"] = "https://workersandbox.mturk.com/mturk/externalSubmit"
    }
    params['anchor'] = await get_anchor(params['task'])

    //maybe something went wrong
    //should we bail instead of displaying the "PLAY" link?
    if(!params["task"]){params["task"] = "no_task";}
    if(!params["timepoint"]){params["timepoint"] = 1;}
    return(params)
}

function params_to_taskurl(params){
    return("/" + params.id + "/" + params.task + "/" + params.timepoint + "/" + 1 + "/");
}

function popup(url) {
    //window.alert("popup for "+url)
    popup = window.open(url,"Popup",
                        "toolbar=no,location=no,status=no,menubar=no,"+
                        "scrollbars=no,width=1024,height=768");//,resizable=no
    return(false);
}

// adds a link to the task if assignmentId is reasonable
// see taskCompleteCode for auto submitting
async function append_play_link(){
   params = await url_params()
   if(!params){return;}
   url = params_to_taskurl(params)
   console.log(params)
   anchors = params.anchor  + "&noinstruction&fewtrials"
   cmd = "popup('" + url +"#"+ anchors +"')"
   document.body.innerHTML +=
        "<br> <a class='clickme' href='#' onClick=\""+cmd+"\">PLAY!</a>"+
        "<br><br><form method=post action='"+ params.external +"'>"+
        "Completion code: <input size=5 id=completecode value='' name='check'><br>"+
        "<input type=hidden name=assignmentId value='"+params.assignmentId+"'>" +
        "<input id=mtruksubmit type='submit' value='COMPLETE'></form>";
}


// set completion code and try to submit form
// hopefully triggered by the popup this page opens
function taskCompleteCode(code){
 let codebox =  document.querySelector("input[name='completecode']");
 if(codebox === null){
     console.log("error: cannot find complete code input");
     return(false);
 }
 codebox.value=code;
 codebox.parent.submit();
 return(true);
}
