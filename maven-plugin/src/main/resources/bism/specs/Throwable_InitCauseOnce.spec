pointcut pc1 before MethodCall(* *.*Throwable.initCause(..))
pointcut pc2 after MethodCall(* *.*Throwable.new()) || after MethodCall(* *.*Throwable.new(String))
pointcut pc3 after MethodCall(* *.*Throwable.new(String, Throwable)) || after MethodCall(* *.*Throwable.new(Throwable))

event e1("Closeable_MultipleClose", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Closeable_MultipleClose", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
event e3("Closeable_MultipleClose", name, [getMethodReceiver, getMethodResult]) on pc3 to Monitor.receiveEvents(String, String, List)
