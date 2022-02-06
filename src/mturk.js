function url_params(){
    var url = new URL(document.URL);

    // id and task from mturk. 
    // TODO: should hitId have a lookup in psiclj?
    params = {"id": url.searchParams.get("assignmentId"),
              "task": url.searchParams.get("hitId"),
              "hash": url.hash};
    
    //if preview. dont show the "PLAY" link
    if(!params["id"] ||
       params["id"] === "ASSIGNMENT_ID_NOT_AVAILABLE"){
        return(undefined);
    }

    //maybe something went wrong
    if(!params["task"]){params["task"] = "no_task";}
    return(params)
}

function params_to_taskurl(params){
    return("/" + params.id + "/" + params.task + "/" + 1 + "/" + 1 + "/");
}

function popup(url) {
    window.alert("popup for"+url)
    popup = window.open(url,"Popup",
                        "toolbar=no,location=no,status=no,menubar=no,"+
                        "scrollbars=no,width=1024,height=768");//,resizable=no
    return(false);
}

// adds a link to the task if assignmentId is reasonable
function append_play_link(){
   params = url_params()
   if(!params){return;}
   url = params_to_taskurl(params)
   cmd = "popup('" + url +"')"
   document.body.innerHTML += "<br> <a class='clickme' href='#' onClick=\""+cmd+"\">PLAY!</a>";
}
