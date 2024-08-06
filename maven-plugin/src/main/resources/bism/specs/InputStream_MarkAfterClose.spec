pointcut pc1 before MethodCall(* *.*InputStream.close(..))
pointcut pc2 before MethodCall(* *.*InputStream.mark(..))

event e1("InputStream_MarkAfterClose", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("InputStream_MarkAfterClose", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
