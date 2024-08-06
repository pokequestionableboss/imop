pointcut pc1 before MethodCall(* *.*OutputStream.close(..))
pointcut pc2 before MethodCall(* *.*OutputStream.write*(..)) || before MethodCall(* *.*OutputStream.flush(..))

event e1("OutputStream_ManipulateAfterClose", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("OutputStream_ManipulateAfterClose", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
