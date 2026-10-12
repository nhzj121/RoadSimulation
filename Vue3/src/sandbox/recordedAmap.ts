import {makeAmapPort} from './recordedMapRenderer'
import type {RecordedMapFactory} from './recordedMapRenderer'

// User-confirmed reuse of MapContainer.vue's existing local development settings.
// Keep ordinary MapContainer behavior untouched; sandbox requests no driving/animation plugin.
const developmentConfiguration={key:'e0ea478e44e417b4c2fc9a54126debaa',securityJsCode:'9df38c185c95fa1dbf78a1082b64f668',version:'2.0'}
/** The bundled loader caches script failure. Reset only an absent SDK, never an existing ordinary map. */
async function developmentSdkModule(){
  const sdk=(await import('@amap/amap-jsapi-loader')).default
  return{load:(options:any)=>sdk.load(options),reset:()=>{
    // Version 1.0.1 exposes reset at runtime but omits it from its declarations.
    const reset=(sdk as unknown as {reset?:()=>void}).reset
    if(typeof reset!=='function')throw new Error('SDK_RESET_UNAVAILABLE')
    reset.call(sdk)
  }}
}
export function createRecordedSdkLoader(module:()=>Promise<{load:(options:any)=>Promise<any>;reset:()=>void}>=developmentSdkModule,browser:()=>any=()=>window){
  let failed=false
  return async(options:any)=>{
    const loader=await module(),surface=browser()
    if(failed&&!surface.AMap&&!surface.AMapUI&&!surface.Loca){loader.reset();failed=false}
    try{return await loader.load(options)}catch(error){failed=true;throw error}
  }
}
export function createRecordedAmapFactory(load:(options:any)=>Promise<any>=createRecordedSdkLoader(),browser:()=>any=()=>window):RecordedMapFactory {
  return async element=>{
    const surface=browser();surface._AMapSecurityConfig={securityJsCode:developmentConfiguration.securityJsCode}
    const amap=await load({key:developmentConfiguration.key,version:developmentConfiguration.version,plugins:[]})
    return makeAmapPort(amap,element)
  }
}
