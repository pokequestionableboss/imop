package org.iemop.instrumentation.library.repair;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeleteClassesFromLog {

    public static boolean delete(List<String> errorLog, String targetPath, boolean isJar) {
        Set<String> delete = new HashSet<>();

        Pattern pattern1 = Pattern.compile("problem generating method (.*) : Code size too big"); // problem generating method polyglot.ext.jl.parse.CUP$Grm$actions.CUP$Grm$do_action : Code size too big: 151181
        Pattern pattern2 = Pattern.compile("when weaving type (.*)"); // when weaving type org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter
        Pattern pattern3 = Pattern.compile("Unexpected problem whilst preparing bytecode for (.*)"); // Unexpected problem whilst preparing bytecode for com.amazonaws.services.ec2.model.transform.RequestSpotFleetRequestMarshaller.marshall(Lcom/amazonaws/services/ec2/model/RequestSpotFleetRequest;)Lcom/amazonaws/Request;
        for (String line : errorLog) {
            Matcher match = pattern1.matcher(line);
            if (match.find()) {
                String method = match.group(1);
                delete.add(method.substring(0, method.lastIndexOf(".")).replace(".", File.separator));
                continue;
            }

            match = pattern2.matcher(line);
            if (match.find()) {
                delete.add(match.group(1).replace(".", File.separator));
                continue;
            }

            match = pattern3.matcher(line);
            if (match.find()) {
                String method = match.group(1).split("\\(")[0];
                delete.add(method.substring(0, method.lastIndexOf(".")).replace(".", File.separator));
            }
        }

        if (delete.isEmpty()) {
            System.out.println("Cannot find any file to skip");
            return false;
        }

        try {
            if (isJar) {
                try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + targetPath), new HashMap<String, String>())) {
                    for (String file : delete) {
                        Path path = fs.getPath("/" + file + ".class");
                        System.out.println("Deleted file " + path + " from jar " + targetPath);
                        Files.deleteIfExists(path);
                    }
                }
            } else {
                for (String file : delete) {
                    Path path = Paths.get(targetPath + File.separator + file + ".class");
                    System.out.println("Deleted file " + path + " from jar " + targetPath);
                    Files.deleteIfExists(path);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
