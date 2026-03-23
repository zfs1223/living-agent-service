use crate::channel::{ChannelError, ChannelConfig};
use crossbeam::channel::{self, Sender, Receiver, TryRecvError, TrySendError, RecvTimeoutError};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

#[allow(dead_code)]
pub struct MpscChannel<T> {
    name: String,
    sender: Sender<T>,
    receiver: Receiver<T>,
    closed: Arc<AtomicBool>,
    capacity: usize,
}

pub struct MpscSender<T> {
    sender: Sender<T>,
    closed: Arc<AtomicBool>,
}

#[allow(dead_code)]
pub struct MpscReceiver<T> {
    receiver: Receiver<T>,
    closed: Arc<AtomicBool>,
}

impl<T: Send + Clone> MpscChannel<T> {
    pub fn new(config: ChannelConfig) -> Self {
        let (sender, receiver) = channel::bounded(config.capacity);
        
        Self {
            name: config.name,
            sender,
            receiver,
            closed: Arc::new(AtomicBool::new(false)),
            capacity: config.capacity,
        }
    }
    
    pub fn sender(&self) -> MpscSender<T> {
        MpscSender {
            sender: self.sender.clone(),
            closed: self.closed.clone(),
        }
    }
    
    pub fn receiver(&self) -> MpscReceiver<T> {
        MpscReceiver {
            receiver: self.receiver.clone(),
            closed: self.closed.clone(),
        }
    }
    
    pub fn split(self) -> (MpscSender<T>, MpscReceiver<T>) {
        (
            MpscSender {
                sender: self.sender,
                closed: self.closed.clone(),
            },
            MpscReceiver {
                receiver: self.receiver,
                closed: self.closed,
            },
        )
    }
}

impl<T: Send> MpscSender<T> {
    pub fn send(&self, message: T) -> Result<(), ChannelError> {
        if self.closed.load(Ordering::Relaxed) {
            return Err(ChannelError::Closed);
        }
        
        self.sender.send(message).map_err(|_| ChannelError::Closed)
    }
    
    pub fn try_send(&self, message: T) -> Result<(), ChannelError> {
        if self.closed.load(Ordering::Relaxed) {
            return Err(ChannelError::Closed);
        }
        
        self.sender.try_send(message).map_err(|e| match e {
            TrySendError::Full(_) => ChannelError::Full,
            TrySendError::Disconnected(_) => ChannelError::Closed,
        })
    }
}

impl<T: Send> MpscReceiver<T> {
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
