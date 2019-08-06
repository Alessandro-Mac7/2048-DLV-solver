package dlv;

import core.TileDLV;
import java.util.LinkedList;

public  class Solver{

public Solver(){
}
public int solve(LinkedList<TileDLV> tiles){
LinkedList<TileDLV> result= new LinkedList<TileDLV>();

	// ---- START - startProgram ---- 
it.unical.mat.jdlv.program.JDLV_Logger.getInstance().logInfoMessage("Creation EXECUTING JDLV module.");
it.unical.mat.wrapper.DLVInputProgram _JDLV_PROGRAM_EXECUTING=new it.unical.mat.wrapper.DLVInputProgramImpl();
_JDLV_PROGRAM_EXECUTING.cleanText();
java.lang.StringBuffer _JDLV_PROGRAM_BUFFER_EXECUTING=new java.lang.StringBuffer();
it.unical.mat.wrapper.DLVInvocation _JDLV_INVOCATION_EXECUTING;

	// ---- END - startProgram ---- 

	// ---- START - addInMapping ---- 
_JDLV_PROGRAM_EXECUTING.addText(it.unical.mat.jdlv.program.TypeSolver.getNameTranslation(tiles,"m"));
it.unical.mat.jdlv.program.JDLV_Logger.getInstance().logInfoMessage("Add in-mapping 'tiles::m' in module EXECUTING:"+ it.unical.mat.jdlv.program.JDLV_Logger.getInstance().getPrettyCode(it.unical.mat.jdlv.program.TypeSolver.getNameTranslation(tiles,"m"), 0));

	// ---- END - addInMapping ---- 
it.unical.mat.jdlv.program.JDLV_Logger.getInstance().logInfoMessage("Add out-mapping 'result::res' in module EXECUTING.");

	// ---- START - prepareJDLVCall ---- 
try{

_JDLV_INVOCATION_EXECUTING=it.unical.mat.wrapper.DLVWrapper.getInstance().createInvocation(it.unical.mat.jdlv.util.JdlvProperties.getInstance().getDlvExecutablePath());
_JDLV_PROGRAM_EXECUTING.addText(_JDLV_PROGRAM_BUFFER_EXECUTING.toString());
_JDLV_PROGRAM_EXECUTING.addText("#maxint = 200."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("size(16)."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("non_res(X, Y, V, M, S, ID) v res(X, Y, V, M, S, ID) :- m(X, Y, V, M, S, ID)."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("contaMerge(ID, N, R) :- m(_, _, _, _, _, ID), #count{M,X,Y : m(X, Y, V, M, S, ID), M != 0} = N, #max{V1 : m(X1, Y1, V1, M1, S1, ID), M1 != 0} = R."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("maxMerge(R) :- #max{N : contaMerge(_, N, _)} = R."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("maxVal(R) :- #max{N : contaMerge(_, _, N)} = R."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("tileMax(ID, N) :- contaMerge(ID, N, _), maxMerge(N)."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("sommaScore(ID, N) :- m(_, _, _, _, _, ID), #sum{S,X,Y : m(X, Y, V, M, S, ID)} = N."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("maxScore(R) :- #max{N : sommaScore(_, N)} = R."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("tileMaxSafe(ID) :- tileMax(ID, _)."+'\n');
_JDLV_PROGRAM_EXECUTING.addText(":- res(_, _, _, _, _, ID), not tileMaxSafe(ID)."+'\n');
_JDLV_PROGRAM_EXECUTING.addText(":- #count{ID : res(_, _, _, _, _, ID)} = 0."+'\n');
_JDLV_PROGRAM_EXECUTING.addText("tileVuoti(ID, Z) :- m(_, _, _, _, _, ID), #count{V,X,Y : m(X, Y, V, M, S, ID), M != 0} = R, size(L), #absdiff(R, L, Z)."+'\n');
_JDLV_PROGRAM_EXECUTING.addText(":- res(_, _, _, _, _, ID), res(_, _, _, _, _, ID2), ID != ID2."+'\n');
_JDLV_PROGRAM_EXECUTING.addText(":~ res(_, _, _, _, _, ID), sommaScore(ID, S), maxScore(M), #absdiff(S, M, Z). [Z:1]"+'\n');
_JDLV_PROGRAM_EXECUTING.addText(":~ res(_, _, _, _, _, ID), contaMerge(ID, _, S), maxVal(M), #absdiff(S, M, Z). [Z:2]"+'\n');
_JDLV_PROGRAM_EXECUTING.addText(":~ res(_, _, _, _, _, ID), tileVuoti(ID, S), size(L), #absdiff(S, L, Z). [Z:3]"+'\n');
_JDLV_PROGRAM_EXECUTING.getFiles().clear();
_JDLV_INVOCATION_EXECUTING.setNumberOfModels(1);
_JDLV_PROGRAM_BUFFER_EXECUTING.append("");
_JDLV_INVOCATION_EXECUTING.setInputProgram(_JDLV_PROGRAM_EXECUTING);
it.unical.mat.wrapper.ModelBufferedHandler _BUFFERED_HANDLER_EXECUTING=new it.unical.mat.wrapper.ModelBufferedHandler(_JDLV_INVOCATION_EXECUTING);
it.unical.mat.jdlv.program.JDLV_Logger.getInstance().logInfoMessage("Start execution EXECUTING program: executablePath='"+it.unical.mat.jdlv.util.JdlvProperties.getInstance().getDlvExecutablePath()+"', options='"+_JDLV_INVOCATION_EXECUTING.getOptionsString()+"'"+'\n'+"Code execution: "+it.unical.mat.jdlv.program.JDLV_Logger.getInstance().getPrettyCode(_JDLV_INVOCATION_EXECUTING.getInputProgram().getCompleteText(),0));
_JDLV_INVOCATION_EXECUTING.run();
while(_BUFFERED_HANDLER_EXECUTING.hasMoreModels()){
it.unical.mat.wrapper.Model _temporary_JDLV_MODELEXECUTING=_BUFFERED_HANDLER_EXECUTING.nextModel();
it.unical.mat.jdlv.program.TypeSolver.loadPredicate(result, "res",_temporary_JDLV_MODELEXECUTING,TileDLV.class);
it.unical.mat.jdlv.program.JDLV_Logger.getInstance().logInfoMessage("Process new answer_set"+ '\n' + "Results:"+ '\n'+ "result " + result.size() + " elements"+ '\n' + it.unical.mat.jdlv.program.JDLV_Logger.getInstance().getPrettyObject(result,0));
}
if(_JDLV_INVOCATION_EXECUTING.haveModel()==false){
System.out.println( "Nessuna soluzione" );
return  0 ;
}
if(!_JDLV_INVOCATION_EXECUTING.getErrors().isEmpty()){
throw new java.lang.RuntimeException(_JDLV_INVOCATION_EXECUTING.getErrors().get(0).getText());
}
}
catch(java.lang.Throwable _JDLV_EXCEPTION_EXECUTING){
it.unical.mat.jdlv.program.JDLV_Logger.getInstance().logErrorMessage(_JDLV_EXCEPTION_EXECUTING.getMessage());
System.err.println( "ERRORE" );
return  0 ;
}
_JDLV_PROGRAM_EXECUTING.cleanText();

	// ---- END - prepareJDLVCall ---- 
return result.getFirst().getId();
}
}

