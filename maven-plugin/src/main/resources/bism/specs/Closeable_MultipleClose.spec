pointcut pc1 before MethodCall(* *.*Closeable.close(..))

event e1("Closeable_MultipleClose", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
