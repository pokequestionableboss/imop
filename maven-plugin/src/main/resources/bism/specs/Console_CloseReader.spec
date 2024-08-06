pointcut pc1 before MethodCall(* *.*Reader.close(..))
pointcut pc2 before MethodCall(* *.*Console.reader())

event e1("Console_CloseReader", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Console_CloseReader", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
