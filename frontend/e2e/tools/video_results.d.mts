export function writeReport(report:unknown,path?:string):void;
export function prepareVideo(path:string):{runId:string;concat:string};
export function finalizeVideo(path:string,runId:string,video?:string):void;
