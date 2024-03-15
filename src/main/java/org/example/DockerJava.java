package org.example;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.google.common.io.ByteStreams;

import java.io.*;

public class DockerJava {
    public static void main(String[] args) throws IOException {
        // 创建 Docker 客户端
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 使用现有的镜像
        String imageName = "opelos_suqian:suqian";

        // 创建容器
        // /bin/bash Bash解释器的路径;是linux发行版默认的shell(命令解释器)
        // command中有三条命令 其中最后一条为执行matlab程序 ./input/JSD20220802_000000_001500.txt为输入，需要根据实际情况修改
        String command="source /etc/profile && cd /home/Code/Matlab/DynamicAnalysisForSuQian/  &&  ./DynamicAnalysis_RealTime ./input/JSD20220802_000000_001500.txt";
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withCmd("/bin/bash", "-c", command)
                .exec();

        // 启动容器
        dockerClient.startContainerCmd(container.getId()).exec();

        //复制宿主机文件到容器；将加速度数据.txt文件复制到docker容器中，以便matlab程序执行；以下路径根据实际更改
        String hostFilePath = "/home/ubuntu1804/Documents/test.txt";
        String containerPath = "/home/Code/Matlab/DynamicAnalysisForSuQian/input/";

        try (InputStream inputStream = new FileInputStream(hostFilePath)) {
            dockerClient.copyArchiveToContainerCmd(container.getId())
                    .withHostResource(hostFilePath)
                    .withRemotePath(containerPath)
                    .exec();
        }

        // 等待command命令执行完成；25000时间根据实际情况调整；或采用别的方式等待docker容器命令执行完成；
        try {
            Thread.sleep(25000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 将容器中的文件复制到宿主机；matlab执行结果在Monitor_RealTime.csv文件中（此文件中有四行测试结果，新生成结果会追加其后）；路径根据实际情况调整；
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = dockerClient.copyArchiveFromContainerCmd(container.getId(), "/home/Code/Matlab/DynamicAnalysisForSuQian/output/Monitor_RealTime.csv");
        try (InputStream response = copyArchiveFromContainerCmd.exec()) {
            try (OutputStream outputStream = new FileOutputStream("/home/ubuntu1804/Documents/Monitor_RealTime.csv")) {
                ByteStreams.copy(response, outputStream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 获取容器日志
        String logs = dockerClient.logContainerCmd(container.getId())
                .withStdOut(true)
                .withStdErr(true)
                .exec(new ResultCallbackTemplate() {
                    @Override
                    public void onNext(Object o) {
                        System.out.println("Container log: " + o.toString());
                    }
                })
                .toString();

        System.out.println("Container output: " + logs);
        System.out.println("Container ID: " + container.getId());

        // 停止并删除容器
        // 检查容器状态
        InspectContainerResponse inspectContainerResponse = dockerClient.inspectContainerCmd(container.getId()).exec();
        if (inspectContainerResponse.getState().getRunning()) {
            // 停止容器
            dockerClient.stopContainerCmd(container.getId()).exec();
            System.out.println("容器已成功停止");
        } else {
            System.out.println("容器已处于停止状态，无需再次停止");
        }
        dockerClient.removeContainerCmd(container.getId()).exec();

        // 关闭 Docker 客户端连接
        dockerClient.close();
    }
}

