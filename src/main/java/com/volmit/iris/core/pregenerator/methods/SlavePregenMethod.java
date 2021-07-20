/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.pregenerator.methods;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.core.pregenerator.turbo.TurboClient;
import com.volmit.iris.core.pregenerator.turbo.command.*;
import com.volmit.iris.engine.data.mca.NBTWorld;
import com.volmit.iris.engine.headless.HeadlessGenerator;
import com.volmit.iris.engine.headless.HeadlessWorld;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.scheduling.J;
import lombok.Getter;
import org.zeroturnaround.zip.ZipUtil;

import javax.management.RuntimeErrorException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SlavePregenMethod implements PregeneratorMethod
{
    @Getter
    private String address;
    private String nickname;
    private int port;
    private String password;
    private IrisDimension dimension;
    private boolean ready = false;
    private File worldFolder;
    private UUID pack = UUID.randomUUID();
    private long seed;

    public SlavePregenMethod(String nickname, File worldFolder, String address, int port, String password, IrisDimension dimension, long seed)
    {
        this.seed = seed;
        this.worldFolder = worldFolder;
        this.address = address;
        this.port = port;
        this.password = password;
        this.dimension = dimension;
    }

    private TurboClient.TurboClientBuilder connect()
    {
        return TurboClient.builder().address(address).port(port);
    }

    public synchronized void setup()
    {
        if(ready)
        {
            return;
        }

        ready = false;
        try {
            connect().command(TurboInstallPack
                .builder()
                    .dimension(dimension)
                    .pack(pack)
                    .seed(seed)
                .build())
                .output((o) -> {
                    File to = new File(Iris.getTemp(), "send-" + pack.toString() + ".zip");
                    ZipUtil.pack(dimension.getLoader().getDataFolder(), to);

                    try {
                        IO.writeAll(to, o);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    to.deleteOnExit();
                })
                .build().go((response, data) -> {
                    if(response instanceof TurboBusy)
                    {
                        throw new RuntimeException("Service is busy, will try later");
                    }

                    ready = true;
            });
            ready = true;
        } catch (Throwable throwable) {
            if(throwable instanceof RuntimeException)
            {
                ready = false;
                return;
            }

            throwable.printStackTrace();
        }
    }

    public boolean canGenerate()
    {
        if(!ready)
        {
            J.a(this::setup);
        }

        return ready;
    }

    @Override
    public void init() {
        J.a(this::setup);
    }

    @Override
    public void close() {
        if(ready)
        {
            try {
                connect()
                    .command(TurboClose
                        .builder()
                            .pack(pack)
                        .build())
                    .build()
                    .go((__, __b) -> {});
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    @Override
    public void save() {

    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return true;
    }

    @Override
    public String getMethod(int x, int z) {
        return "Remote<" + nickname + ">";
    }

    private double checkProgress(int x, int z)
    {
        AtomicDouble progress = new AtomicDouble(-1);
        try {
            connect()
                .command(TurboGetProgress.builder()
                        .pack(pack).build()).output((i) -> {
                }).build().go((response, o) -> {
                    if(response instanceof TurboSendProgress)
                    {
                        if(((TurboSendProgress) response).isAvailable())
                        {
                            progress.set(((TurboSendProgress) response).getProgress());
                            File f = new File(worldFolder, "region/r." + x + "." + z + ".mca");
                            try {
                                f.getParentFile().mkdirs();
                                IO.writeAll(f, o);
                                progress.set(1000);
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    }
            });
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return progress.get();
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        if(!ready)
        {
            throw new RuntimeException();
        }

        try {
            connect().command(TurboGenerate
                .builder()
                    .x(x).z(z).pack(pack)
                .build())
                .build().go((response, data) -> {
                    if(response instanceof TurboOK)
                    {
                        listener.onNetworkStarted(x, z);
                        J.a(() -> {
                            double lastp = 0;
                            int calls = 0;
                            boolean installed = false;
                            while(true)
                            {
                                J.sleep(100);
                                double progress = checkProgress(x, z);

                                if(progress == 1000)
                                {
                                    installed = true;
                                    AtomicInteger a = new AtomicInteger(calls);
                                    PregenTask.iterateRegion(x, z, (xx, zz) -> {
                                        if(a.decrementAndGet() < 0)
                                        {
                                            listener.onNetworkGeneratedChunk(xx, zz);
                                        }
                                    });
                                    calls = 1024;
                                }

                                else if(progress < 0)
                                {
                                    break;
                                }

                                int change = (int) Math.floor((progress - lastp) * 1024D);
                                change = change == 0 ? 1 : change;

                                AtomicInteger a = new AtomicInteger(calls);
                                AtomicInteger b = new AtomicInteger(change);
                                PregenTask.iterateRegion(x, z, (xx, zz) -> {
                                    if(a.decrementAndGet() < 0)
                                    {
                                        if(b.decrementAndGet() >= 0)
                                        {
                                            listener.onNetworkGeneratedChunk(xx, zz);
                                        }
                                    }
                                });
                                calls += change;
                            }

                            if(!installed)
                            {
                                // TODO RETRY REGION
                                return;
                            }

                            listener.onNetworkDownloaded(x, z);
                        });
                    }

                    else if(response instanceof TurboInstallFirst)
                    {
                        ready = false;
                        throw new RuntimeException();
                    }

                    else if(response instanceof TurboBusy)
                    {
                        throw new RuntimeException();
                    }

                    else
                    {
                        throw new RuntimeException();
                    }
            });
        } catch (Throwable throwable) {

            if(throwable instanceof RuntimeException)
            {
                throw (RuntimeException) throwable;
            }

            throwable.printStackTrace();
        }
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {

    }
}