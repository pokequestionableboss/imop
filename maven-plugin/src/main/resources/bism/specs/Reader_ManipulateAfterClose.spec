pointcut pc1 before MethodCall(* *.*Reader.close(..))
pointcut pc2 before MethodCall(* *.*Reader.read(..)) || before MethodCall(* *.*Reader.ready(..)) || before MethodCall(* *.*Reader.mark(..)) || before MethodCall(* *.*Reader.reset(..))|| before MethodCall(* *.*Reader.skip(..))

event e1("Reader_ManipulateAfterClose", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Reader_ManipulateAfterClose", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
