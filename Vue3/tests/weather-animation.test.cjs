const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../src/components/MapContainer.vue'), 'utf8');
const classes = source.slice(source.indexOf('class VehicleAnimation {'), source.indexOf('// ==================== 车辆动画管理器类'));
function createAnimation(vrp = false) {
  const context = { console: {log(){},warn(){}}, Math, Number, Map, performance: {now:()=>100}, requestAnimationFrame:()=>1, cancelAnimationFrame(){}, setTimeout(){}, request:{post:async()=>({})} };
  vm.createContext(context);
  vm.runInContext(classes + '\nthis.Animation = VehicleAnimation; this.Vrp = VrpVehicleAnimation;', context);
  const assignment = {assignmentId:5,vehicleId:8,licensePlate:'TEST',endPOIId:20};
  const statuses=[];
  const route={assignment, stage1Path:[[0,0],[0.01,0]],stage2Path:[[0.01,0],[0.02,0]],movingMarker:{setPosition(){}}};
  route.stages=[{path:route.stage1Path,nodeInfo:{poiId:10,actionType:'LOAD'}},{path:route.stage2Path,nodeInfo:{poiId:20,actionType:'UNLOAD'}}];
  const animation=new (vrp?context.Vrp:context.Animation)(assignment,route,{updateVehicleStatus:(id,status)=>statuses.push(status)});
  return {animation,statuses};
}
function snapshot(overrides={}) { return {assignmentId:5,status:'TRANSPORT_DRIVING',drivingStatus:'TRANSPORT_DRIVING',drivingPhaseKey:'phase-1',drivingLegIndex:1,drivingProgress:0.5,effectiveSpeedFactor:0.32,...overrides}; }

test('monitor restores correct stage and progress; user speed remains independent',()=>{
  const {animation:a}=createAnimation(); a.speedFactor=20; a.updateDrivingSnapshot(snapshot());
  assert.equal(a.currentStage,2); assert.equal(a.currentProgress,0.5); assert.equal(a.eventSpeedFactor,0.32); assert.equal(a.speedFactor,20);
  a.start(); assert(a.animationTime>0); // starting after refresh must not reset restored position
});
test('breakdown freezes visual progress and backend status wins',()=>{
  const {animation:a,statuses}=createAnimation();
  a.updateDrivingSnapshot(snapshot({status:'BREAKDOWN',effectiveSpeedFactor:0}));
  a._animateAuthoritative(100); assert.equal(a.currentProgress,0.5); assert.equal(statuses.at(-1),'BREAKDOWN');
});
test('visual interpolation cannot complete an unfinished server phase',()=>{
  const {animation:a}=createAnimation(); let calls=0; a._acknowledgeDrivingPhase=()=>calls++;
  a.updateDrivingSnapshot(snapshot({drivingProgress:0.98})); a._animateAuthoritative(10000);
  assert(a.currentProgress<1); assert.equal(calls,0);
});
test('VRP restores server leg and load without replaying local loading mutations',()=>{
  const {animation:a,statuses}=createAnimation(true);
  a.updateDrivingSnapshot(snapshot({status:'BREAKDOWN',effectiveSpeedFactor:0,currentLoad:17,currentVolume:3}));
  a._animateAuthoritative(100);
  assert.equal(a.currentStageIndex,1); assert.equal(a.runtimeLoad,17); assert.equal(a.runtimeVolume,3); assert.equal(statuses.at(-1),'BREAKDOWN');
});
test('completed phase uses matching leg and phase id in acknowledgement',async()=>{
  const {animation:a}=createAnimation(true);
  a.updateDrivingSnapshot(snapshot({drivingProgress:1}));
  await a._acknowledgeDrivingPhase(a.drivingSnapshot); assert.equal(a.acknowledgedPhase,'phase-1');
});

test('weather mode waits for missing snapshot instead of advancing local business state',()=>{
  const {animation:a}=createAnimation(); let completed=0;
  a.authoritativeEnvironment=true; a._completeCurrentStage=()=>completed++;
  a.updateDrivingSnapshot(null); a.animationTime=1e9; a._animate();
  assert.equal(completed,0); assert.equal(a.currentStage,1);
});

test('a stale other-assignment snapshot cannot move this vehicle animation',()=>{
  const {animation:a}=createAnimation(); a.authoritativeEnvironment=true;
  a.updateDrivingSnapshot(snapshot({assignmentId:99}));
  assert.equal(a.drivingSnapshot,null); assert.equal(a.currentStage,1);
});

test('unloading snapshot uses completed transport phase acknowledgement without local load mutation',()=>{
  const {animation:a}=createAnimation(); let calls=0;
  a._acknowledgeDrivingPhase=()=>calls++;
  a.updateDrivingSnapshot(snapshot({status:'UNLOADING',drivingProgress:1}));
  a._animateAuthoritative(100); assert.equal(calls,1); assert.equal(a.currentProgress,1);
});

for (const running of [true, false]) test(`refresh restores ${running ? 'running' : 'paused'} weather run without starting backend`, async()=>{
  const calls=[];
  const ctx={updateVehicleInfo:async()=>{},monitorWeather:{value:{runId:'saved-run'}},isExperimentRunActive:{value:false},
    simulationController:{getConfig:async()=>({success:true,data:{running}})},beginSimulationGeneration:()=>7,
    isSimulationRunning:{value:false},restoringWeatherView:false,
    animationManager:{isPaused:false,startAll:()=>calls.push('start'),pauseAll:()=>calls.push('pause')},
    fetchCurrentAssignments:async generation=>{assert.equal(generation,7);assert.equal(ctx.restoringWeatherView,true);assert.equal(ctx.animationManager.isPaused,!running);calls.push('draw');},
    isActiveTransportGeneration:()=>running,startSimulationTimer:()=>calls.push('timer'),
    arrivalMonitor:{startMonitoring:()=>calls.push('monitor')},getVehiclePositions(){},getPOIList(){}};
  vm.createContext(ctx);
  const method=source.slice(source.indexOf('const restoreWeatherRunView ='),source.indexOf('onMounted(() => {',source.indexOf('const restoreWeatherRunView =')));
  vm.runInContext(method+'\nthis.restore = restoreWeatherRunView;',ctx);
  await ctx.restore();
  assert.equal(ctx.restoringWeatherView,false);assert.equal(ctx.isSimulationRunning.value,running);
  assert.deepEqual(calls,running?['draw','start','timer','monitor']:['draw','pause']);
});
