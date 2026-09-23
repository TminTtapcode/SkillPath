import { useState } from 'react'
import { Link } from 'react-router'

type ArchitectureNode = 'FRONTEND' | 'BACKEND' | 'DATABASE' | 'NETWORK'

export function SystemArchitectureGraph() {
  const [selectedNode, setSelectedNode] = useState<ArchitectureNode | null>(null)

  const nodeDetails: Record<ArchitectureNode, { title: string; description: string; example: string; learn: string[] }> = {
    FRONTEND: {
      title: 'Frontend (Client)',
      description: 'The part of the application that users see and interact with directly. It runs in the browser or mobile app.',
      example: 'User clicks "Show Products", the Frontend sends a request to the Backend and then draws the product list on screen.',
      learn: ['HTML/CSS', 'JavaScript', 'React', 'UI/UX'],
    },
    BACKEND: {
      title: 'Backend (Server)',
      description: 'The engine running on a server that processes business logic, handles security, and fetches data.',
      example: 'Receives the "Show Products" request, applies business logic (e.g. is user logged in?), fetches data from DB, and returns JSON.',
      learn: ['HTTP / API', 'Java', 'Spring Boot', 'Security'],
    },
    DATABASE: {
      title: 'Database',
      description: 'The structured storage system that permanently holds the application\'s data.',
      example: 'Stores the list of all products, user profiles, and order history securely.',
      learn: ['SQL', 'Relational Databases', 'JPA / Hibernate'],
    },
    NETWORK: {
      title: 'Network',
      description: 'The communication layer (the internet) that allows the Frontend and Backend to talk to each other.',
      example: 'Transmits the HTTP request from the user\'s phone in Vietnam to the server in Singapore.',
      learn: ['HTTP', 'TCP/IP', 'DNS'],
    },
  }

  return (
    <section className="panel onboarding-page" style={{ maxWidth: '900px', margin: '2rem auto' }}>
      <div className="page-header-block" style={{ textAlign: 'center', marginBottom: '2rem' }}>
        <p className="eyebrow">SkillPath Onboarding</p>
        <h1>How a Web System Works</h1>
        <p className="lede">
          Before you choose a learning track, click around this "Mental Map" to understand the fundamental pieces of modern software.
        </p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 350px', gap: '2rem' }}>
        {/* The Graph */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '2rem', padding: '2rem', background: 'var(--sp-surface-card)', borderRadius: 'var(--sp-radius-xl)', border: '2px solid var(--sp-border-default)', boxShadow: '0 4px 0 0 var(--sp-border-subtle)' }}>
          <div style={{ padding: '0.5rem 1rem', background: '#f8fafc', border: '2px dashed #94a3b8', borderRadius: '8px', fontWeight: 'bold' }}>
            User Device (Browser)
          </div>
          
          <button 
            type="button"
            className={`planner-task ${selectedNode === 'FRONTEND' ? 'selected' : ''}`}
            onClick={() => setSelectedNode('FRONTEND')}
            style={{ width: '200px', textAlign: 'center', borderColor: selectedNode === 'FRONTEND' ? 'var(--sp-primary)' : '' }}
          >
            Frontend
          </button>
          
          {/* Arrow */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <div style={{ height: '40px', width: '2px', background: 'var(--sp-primary)', position: 'relative' }}>
              <div style={{ position: 'absolute', bottom: '-5px', left: '-4px', width: '0', height: '0', borderLeft: '5px solid transparent', borderRight: '5px solid transparent', borderTop: '6px solid var(--sp-primary)' }} />
            </div>
            <button
              type="button"
              className={`planner-task ${selectedNode === 'NETWORK' ? 'selected' : ''}`}
              style={{ padding: '0.5rem 1rem', borderColor: selectedNode === 'NETWORK' ? 'var(--sp-primary)' : '' }}
              onClick={() => setSelectedNode('NETWORK')}
            >
              Network (HTTP)
            </button>
          </div>

          <button 
            type="button"
            className={`planner-task ${selectedNode === 'BACKEND' ? 'selected' : ''}`}
            onClick={() => setSelectedNode('BACKEND')}
            style={{ width: '200px', textAlign: 'center', borderColor: selectedNode === 'BACKEND' ? 'var(--sp-primary)' : '' }}
          >
            Backend
          </button>

          {/* Arrow */}
          <div style={{ height: '40px', width: '2px', background: 'var(--sp-primary)', position: 'relative' }}>
             <div style={{ position: 'absolute', bottom: '-5px', left: '-4px', width: '0', height: '0', borderLeft: '5px solid transparent', borderRight: '5px solid transparent', borderTop: '6px solid var(--sp-primary)' }} />
          </div>

          <button 
            type="button"
            className={`planner-task ${selectedNode === 'DATABASE' ? 'selected' : ''}`}
            onClick={() => setSelectedNode('DATABASE')}
            style={{ width: '200px', textAlign: 'center', borderColor: selectedNode === 'DATABASE' ? 'var(--sp-primary)' : '' }}
          >
            Database
          </button>
        </div>

        {/* The Explanation Detail Panel */}
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {selectedNode ? (
            <div className="planner-task" style={{ borderColor: 'var(--sp-primary)', height: '100%' }}>
              <h2 style={{ marginTop: 0 }}>{nodeDetails[selectedNode].title}</h2>
              <p style={{ color: 'var(--sp-text-secondary)', lineHeight: '1.6' }}>
                {nodeDetails[selectedNode].description}
              </p>
              
              <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px', borderLeft: '3px solid #6366f1', margin: '1.5rem 0' }}>
                <strong style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.85rem', color: '#4f46e5', textTransform: 'uppercase' }}>Example</strong>
                <span style={{ fontSize: '0.9rem' }}>{nodeDetails[selectedNode].example}</span>
              </div>

              <div>
                <strong style={{ fontSize: '0.85rem', color: 'var(--sp-text-muted)', textTransform: 'uppercase' }}>You will learn:</strong>
                <ul style={{ paddingLeft: '1.25rem', marginTop: '0.5rem', color: 'var(--sp-text-secondary)', fontWeight: '600' }}>
                  {nodeDetails[selectedNode].learn.map((item, idx) => (
                    <li key={idx} style={{ marginBottom: '0.25rem' }}>{item}</li>
                  ))}
                </ul>
              </div>
            </div>
          ) : (
            <div className="planner-task" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', color: 'var(--sp-text-muted)', height: '100%', borderStyle: 'dashed' }}>
              <p>Click on any block to see how it works and what you will learn.</p>
            </div>
          )}
        </div>
      </div>

      <div style={{ marginTop: '2.5rem', textAlign: 'center', padding: '2rem 0', borderTop: '2px solid var(--sp-border-subtle)' }}>
        <p style={{ color: 'var(--sp-text-secondary)', marginBottom: '1.5rem', fontWeight: 'bold' }}>Ready to pick your learning track?</p>
        <Link to="/goals/new" className="today-start-btn button-link">
          Choose a Learning Track
        </Link>
      </div>
    </section>
  )
}
