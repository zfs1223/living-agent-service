use crate::channel::{ChannelError, ChannelConfig};
use crossbeam::channel::{self, Sender, Receiver, TryRecvError, TrySendError, RecvTimeoutError};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use parking_lot::RwLock;

pub struct BroadcastChannel<T> {
    name: String,
    subscribers: Arc<RwLock<Vec<Sender<T>>>>,
    closed: Arc<AtomicBool>,
    capacity: usize,
}

pub struct BroadcastSender<T> {
    subscribers: Arc<RwLock<Vec<Sender<T>>>>,
    closed: Arc<AtomicBool>,
}

pub struct BroadcastReceiver<T> {
    receiver: Receiver<T>,
    closed: Arc<AtomicBool>,
}

impl<T: Send + Clone> BroadcastChannel<T> {
    pub fn new(config: ChannelConfig) -> Self {
        Self {
            name: config.name,
            subscribers: Arc::new(RwLock::new(Vec::new())),
            closed: Arc::new(AtomicBool::new(false)),
            capacity: config.capacity,
        }
    }
    
    pub fn subscribe(&self) -> BroadcastReceiver<T> {
        let (sender, receiver) = channel::bounded(self.capacity);
        
        {
            let mut subscribers = self.subscribers.write();
            subscribers.push(sender);
        }
        
        BroadcastReceiver {
            receiver,
            closed: self.closed.clone(),
        }
    }
    
    pub fn sender(&self) -> BroadcastSender<T> {
        BroadcastSender {
            subscribers: self.subscribers.clone(),
            closed: self.closed.clone(),
        }
    }
    
    pub fn subscriber_count(&self) -> usize {
        self.subscribers.read().len()
    }
}

impl<T: Send + Clone> BroadcastSender<T> {
    pub fn broadcast(&self, message: T) -> Result<usize, ChannelError> {
        if self.closed.load(Ordering::Relaxed) {
            return Err(ChannelError::Closed);
        }
        
        let mut sent_count = 0;
        let mut to_remove = Vec::new();
        
        {
            let subscribers = self.subscribers.read();
            for (idx, sender) in subscribers.iter().enumerate() {
                match sender.try_send(message.clone()) {
                    Ok(_) => sent_count += 1,
                    Err(_) => {
                        to_remove.push(idx);
                    }
                }
            }
        }
        
        if !to_remove.is_empty() {
            let mut subscribers = self.subscribers.write();
            for idx in to_remove.into_iter().rev() {
                subscribers.remove(idx);
            }
        }
        
        Ok(sent_count)
    }
    
    pub fn try_broadcast(&self, message: T) -> Result<usize, ChannelError> {
        self.broadcast(message)
    }
}

impl<T: Send> BroadcastReceiver<T> {
    pub fn recv(&self) -> Result<T, ChannelError> {
        self.receiver.recv().map_err(|_| ChannelError::Closed)
    }
    
    pub fn try_recv(&self) -> Result<T, ChannelError> {
        self.receiver.try_recv().map_err(|e| match e {
            TryRecvError::Empty => ChannelError::Empty,
            TryRecvError::Disconnected => ChannelError::Closed,
        })
    }
    
    pub fn recv_timeout(&self, timeout: std::time::Duration) -> Result<T, ChannelError> {
        self.receiver.recv_timeout(timeout).map_err(|e| match e {
            RecvTimeoutError::Timeout => ChannelError::RecvTimeout,
            RecvTimeoutError::Disconnected => ChannelError::Closed,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_broadcast_channel() {
        let channel = BroadcastChannel::new(ChannelConfig::default());
        let sender = channel.sender();
        let receiver1 = channel.subscribe();
        let receiver2 = channel.subscribe();
        
        sender.broadcast("hello".to_string()).unwrap();
        
        assert_eq!(receiver1.try_recv().unwrap(), "hello");
        assert_eq!(receiver2.try_recv().unwrap(), "hello");
    }
}
