package com.livingagent.skill.hotreload;

import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.skill.service.SkillBindingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SkillHotReloader {
    
    private static final Logger log = LoggerFactory.getLogger(SkillHotReloader.class);
    
    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final long DEBOUNCE_MS = 1000;
    
    private final SkillRegistry skillRegistry;
    private final SkillBindingService skillBindingService;
    
    @Value("${skill.data.path:./data/skills}")
    private String dataSkillsPath;
    
    @Value("${skill.hotreload.enabled:true}")
    private boolean hotReloadEnabled;
    
    @Value("${skill.hotreload.watch.config:true}")
    private boolean watchConfigPath;
    
    @Value("${skill.config.path:./config/skills}")
    private String configSkillsPath;
    
    private WatchService watchService;
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long lastReloadTime = 0;
    
    @Autowired
    public SkillHotReloader(SkillRegistry skillRegistry, SkillBindingService skillBindingService) {
        this.skillRegistry = skillRegistry;
        this.skillBindingService = skillBindingService;
    }
    
    @PostConstruct
    public void startWatching() {
        if (!hotReloadEnabled) {
            log.info("Skill hot reload is disabled");
            return;
        }
        
        log.info("Starting skill hot reloader...");
        
        try {
            watchService = FileSystems.getDefault().newWatchService();
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "skill-hot-reloader");
                t.setDaemon(true);
                return t;
            });
            
            registerWatchDirectory(Paths.get(dataSkillsPath), "data");
            
            if (watchConfigPath) {
                registerWatchDirectory(Paths.get(configSkillsPath), "config");
            }
            
            running.set(true);
            executorService.submit(this::watchLoop);
            
            log.info("Skill hot reloader started, watching: {}", dataSkillsPath);
            
        } catch (Exception e) {
            log.error("Failed to start skill hot reloader: {}", e.getMessage());
        }
    }
    
    private void registerWatchDirectory(Path dir, String label) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.info("Created directory: {}", dir);
        }
        
        dir.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        
        log.info("Registered watch for {} skills: {}", label, dir);
    }
    
    private void watchLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                
                boolean shouldReload = false;
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changedPath = (Path) event.context();
                    String fileName = changedPath.getFileName().toString();
                    
                    if (SKILL_FILE_NAME.equals(fileName)) {
                        log.info("Detected skill file change: {} [{}]", 
                                changedPath, event.kind().name());
                        shouldReload = true;
                    } else {
                        Path watchablePath = (Path) key.watchable();
                        Path fullPath = watchablePath.resolve(changedPath);
                        if (Files.isDirectory(fullPath)) {
                            handleDirectoryChange(watchablePath, changedPath, event.kind());
                        }
                    }
                }
                
                if (shouldReload) {
                    debouncedReload();
                }
                
                key.reset();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in watch loop: {}", e.getMessage());
            }
        }
    }
    
    private void handleDirectoryChange(Path parent, Path changedDir, WatchEvent.Kind<?> kind) {
        try {
            Path fullPath = parent.resolve(changedDir);
            
            if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                fullPath.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                log.info("Registered new skill directory: {}", fullPath);
            }
        } catch (IOException e) {
            log.warn("Failed to handle directory change: {}", e.getMessage());
        }
    }
    
    private synchronized void debouncedReload() {
        long now = System.currentTimeMillis();
        if (now - lastReloadTime < DEBOUNCE_MS) {
            log.debug("Debouncing skill reload request");
            return;
        }
        lastReloadTime = now;
        
        log.info("Hot reloading skills due to file change...");
        
        try {
            skillRegistry.reloadSkills();
            
            log.info("Skills hot reloaded successfully");
            
        } catch (Exception e) {
            log.error("Failed to hot reload skills: {}", e.getMessage());
        }
    }
    
    public void manualReload() {
        log.info("Manual skill reload triggered");
        debouncedReload();
    }
    
    public void reloadSkill(String skillName) {
        log.info("Reloading single skill: {}", skillName);
        skillRegistry.reloadSkills();
    }
    
    @PreDestroy
    public void stopWatching() {
        log.info("Stopping skill hot reloader...");
        running.set(false);
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Failed to close watch service: {}", e.getMessage());
            }
        }
        
        log.info("Skill hot reloader stopped");
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public boolean isHotReloadEnabled() {
        return hotReloadEnabled;
    }
    
    public String getWatchedPath() {
        return dataSkillsPath;
    }
}
