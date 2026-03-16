pub fn cosine_similarity(a: &[f32], b: &[f32]) -> f32 {
    if a.len() != b.len() || a.is_empty() {
        return 0.0;
    }
    
    cosine_similarity_scalar(a, b)
}

fn cosine_similarity_scalar(a: &[f32], b: &[f32]) -> f32 {
    let mut dot_product = 0.0_f32;
    let mut norm_a = 0.0_f32;
    let mut norm_b = 0.0_f32;
    
    for i in 0..a.len() {
        dot_product += a[i] * b[i];
        norm_a += a[i] * a[i];
        norm_b += b[i] * b[i];
    }
    
    let denominator = (norm_a * norm_b).sqrt();
    if denominator > 0.0 {
        dot_product / denominator
    } else {
        0.0
    }
}

pub fn euclidean_distance(a: &[f32], b: &[f32]) -> f32 {
    if a.len() != b.len() || a.is_empty() {
        return f32::MAX;
    }
    
    euclidean_distance_scalar(a, b)
}

fn euclidean_distance_scalar(a: &[f32], b: &[f32]) -> f32 {
    a.iter()
        .zip(b.iter())
        .map(|(x, y)| (x - y).powi(2))
        .sum::<f32>()
        .sqrt()
}

pub fn dot_product(a: &[f32], b: &[f32]) -> f32 {
    if a.len() != b.len() || a.is_empty() {
        return 0.0;
    }
    
    dot_product_scalar(a, b)
}

fn dot_product_scalar(a: &[f32], b: &[f32]) -> f32 {
    a.iter().zip(b.iter()).map(|(x, y)| x * y).sum()
}

pub fn manhattan_distance(a: &[f32], b: &[f32]) -> f32 {
    if a.len() != b.len() {
        return f32::MAX;
    }
    
    a.iter()
        .zip(b.iter())
        .map(|(x, y)| (x - y).abs())
        .sum()
}

pub fn normalize_vector(v: &mut [f32]) {
    let norm: f32 = v.iter().map(|x| x * x).sum::<f32>().sqrt();
    if norm > 0.0 {
        for x in v.iter_mut() {
            *x /= norm;
        }
    }
}

pub fn batch_cosine_similarity(query: &[f32], candidates: &[&[f32]]) -> Vec<f32> {
    candidates.iter().map(|c| cosine_similarity(query, c)).collect()
}

pub fn find_top_k(
    query: &[f32],
    candidates: &[(&str, &[f32])],
    k: usize,
) -> Vec<(String, f32)> {
    let mut scores: Vec<(String, f32)> = candidates
        .iter()
        .map(|(id, vec)| (id.to_string(), cosine_similarity(query, vec)))
        .collect();
    
    scores.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
    scores.truncate(k);
    scores
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_cosine_similarity_identical() {
        let a = vec![1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let b = vec![1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let sim = cosine_similarity(&a, &b);
        assert!((sim - 1.0).abs() < 0.0001);
    }
    
    #[test]
    fn test_cosine_similarity_orthogonal() {
        let a = vec![1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let b = vec![0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let sim = cosine_similarity(&a, &b);
        assert!((sim - 0.0).abs() < 0.0001);
    }
    
    #[test]
    fn test_cosine_similarity_opposite() {
        let a = vec![1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let b = vec![-1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let sim = cosine_similarity(&a, &b);
        assert!((sim - (-1.0)).abs() < 0.0001);
    }
    
    #[test]
    fn test_euclidean_distance() {
        let a = vec![0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let b = vec![1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0];
        let dist = euclidean_distance(&a, &b);
        assert!((dist - 2.8284).abs() < 0.01);
    }
    
    #[test]
    fn test_dot_product() {
        let a = vec![1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0];
        let b = vec![1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0];
        let prod = dot_product(&a, &b);
        assert!((prod - 36.0).abs() < 0.0001);
    }
    
    #[test]
    fn test_normalize_vector() {
        let mut v = vec![3.0, 4.0];
        normalize_vector(&mut v);
        assert!((v[0] - 0.6).abs() < 0.0001);
        assert!((v[1] - 0.8).abs() < 0.0001);
    }
    
    #[test]
    fn test_find_top_k() {
        let query = vec![1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let candidates: Vec<(&str, &[f32])> = vec![
            ("a", &[0.9, 0.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]),
            ("b", &[0.1, 0.9, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]),
            ("c", &[0.8, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]),
        ];
        
        let top_k = find_top_k(&query, &candidates, 2);
        assert_eq!(top_k.len(), 2);
        assert_eq!(top_k[0].0, "a");
        assert_eq!(top_k[1].0, "c");
    }
}
