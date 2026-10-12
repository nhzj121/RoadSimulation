import {ref} from 'vue'
import type {createRecordedMapController} from './recordedMap'
type Model=ReturnType<typeof createRecordedMapController>
export interface PlaybackClock {set(fn:()=>void,ms:number):unknown;clear(handle:unknown):void}
/** UI-only serial playback. Timers never advance simulation or fabricate intermediate positions. */
export function createRecordedPlayback(model:Model,clock:PlaybackClock={set:(fn,ms)=>setTimeout(fn,ms),clear:h=>clearTimeout(h as ReturnType<typeof setTimeout>)}) {
  const playing=ref(false),waiting=ref(false),intervalMs=ref(1000)
  let timer:unknown,generation=0,disposed=false,selectingGeneration:number|undefined
  function stopTimer(){if(timer!==undefined)clock.clear(timer);timer=undefined}
  function pause(){generation++;stopTimer();playing.value=false;waiting.value=false;model.stopPrefetch();if(selectingGeneration!==undefined){model.cancelSelection();selectingGeneration=undefined}}
  function eligible(){return !disposed&&!model.stale.value&&!model.blocked.value&&!model.inspection.value}
  function schedule(token:number){if(playing.value&&token===generation&&!disposed)timer=clock.set(()=>{timer=undefined;void advance(token)},intervalMs.value)}
  async function advance(token:number){
    if(token!==generation||!playing.value)return
    if(!eligible()){pause();return}
    if(model.selecting.value||model.loading.value){schedule(token);return}
    const count=model.summary.value?.progress.recordedLoops||0,index=(model.snapshot.value?.loopIndex??-1)+1
    if(index>=count){if(model.isTerminal())pause();else{waiting.value=true;schedule(token)}return}
    waiting.value=false
    selectingGeneration=token;const ok=await model.select(index,false);if(selectingGeneration===token)selectingGeneration=undefined
    if(token!==generation||!playing.value)return
    if(!ok||!eligible()){pause();return}
    void model.prefetch(index+1);schedule(token)
  }
  async function play(){
    if(playing.value||!eligible()||model.selecting.value||model.loading.value)return
    model.following.value=false;playing.value=true;waiting.value=false;const token=++generation
    const count=model.summary.value?.progress.recordedLoops||0
    // A finished record opens at its last tick: play starts at tick 0; resume elsewhere retains selection.
    if(count>0&&model.isTerminal()&&model.snapshot.value?.loopIndex===count-1){
      selectingGeneration=token;const ok=await model.select(0,false);if(selectingGeneration===token)selectingGeneration=undefined;if(token!==generation)return;if(!ok){pause();return}
    }
    if(token!==generation||!eligible()){pause();return}
    void model.prefetch((model.snapshot.value?.loopIndex??-1)+1);schedule(token)
  }
  function speed(ms:number){if(![500,1000,2000].includes(ms))throw new Error('INVALID_PLAYBACK_SPEED');intervalMs.value=ms;if(playing.value&&timer!==undefined){stopTimer();schedule(generation)}}
  function dispose(){disposed=true;pause()}
  return{playing,waiting,intervalMs,play,pause,speed,dispose}
}
