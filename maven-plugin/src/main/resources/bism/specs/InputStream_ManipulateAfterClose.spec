pointcut pc1 before MethodCall(* *.*InputStream.close(..))
pointcut pc2 before MethodCall(* *.*InputStream.read(..)) || before MethodCall(* *.*InputStream.available(..)) || before MethodCall(* *.*InputStream.reset(..)) || before MethodCall(* *.*InputStream.skip(..))

event e1("InputStream_ManipulateAfterClose", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("InputStream_ManipulateAfterClose", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
