package io.github.demo.constants;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class Foo {

    private static final String cache_dir = "cache";

    public static File getCacheDir(String dir){
        String path = System.getProperty("user.dir")+File.separator+cache_dir+File.separator+dir;
        File file = new File(path);
        if(!file.exists()){
            if(!file.mkdirs()){
                throw new RuntimeException("目录创建失败"+path);
            }
        }
        return file;
    }

    public static File createRandomTemp(File parent){
        String string = UUID.randomUUID().toString()+".tmp";
        File file = new File(parent, string);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

}
